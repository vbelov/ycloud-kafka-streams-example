# Пример использования Kafka Streams с Yandex Managed Service for Apache Kafka®

Пример состоит из 3х небольших приложений:
- python приложение `producer` считывает текст (для примера использована биография Бенжамина Франклина), 
разбивает его на отдельные слова и публикует их в топик `streams-plaintext-input`.
- `streaming-app` это приложение на Scala, использующие библиотеку Kafka Streams. Оно вычитывает отдельные слова из топика 
`streams-plaintext-input`, агреггирует их, подсчитывает количество использований каждого слова и публикует результат 
в топик `streams-wordcount-output`.
- python приложение `consumer` вычитывает агреггированные данные из топика `streams-wordcount-output` и печатает их.

Идея и базовая реализация `streaming-app` взята [отсюда](https://github.com/confluentinc/kafka-streams-examples/blob/6.0.0-post/src/main/scala/io/confluent/examples/streams/WordCountScalaExample.scala), 
но доработана с целью аутентификации.

На момент написания этого туториала создание топиков через admin api в Yandex Managed Service for Apache Kafka® запрещено. 
Топики можно создавать только через API Яндекс.Облака. Также невозможен доступ к топикам без аутентификации. 
Поэтому для корректной работы Kafka Streams необходимо самостоятельно через интерфейсы Яндекс.Облака создавать топики, 
пользователей и настраивать им права доступа.

В данном туториале создание ресурсов будет выполняться с помощью `yc` - инструмента командной строки Яндекс.Облака. 
Инструкцию по его установке и настройке можно найти [здесь](https://cloud.yandex.ru/docs/cli/quickstart). 


## Создаем кластер Kafka, топики и пользователей

Создаем кластер Kafka:
```bash
yc kafka cluster create kafka-streams \
    --environment production \
    --network-name default \
    --brokers-count 1 \
    --zone-ids ru-central1-a,ru-central1-b,ru-central1-c \
    --resource-preset s2.small \
    --disk-size 20 \
    --disk-type network-hdd \
    --version 2.6 \
    --assign-public-ip
```
На момент написания этого туториала еще не опубликована версия yc cli, которая позволяет уменьшить размер сегмента лога. 
Поэтому возможно потребуется увеличить размер диска, к примеру, до 200 ГБ.
В новой версии yc при создании кластера kafka можно будет указать дополнительный параметр `--log-segment-bytes=16777216`.

Запрашиваем список хостов созданного kafka кластера:
```bash
yc kafka cluster list-hosts kafka-streams
```
В дальнейшем нам понадобится fqdn по крайней мере одного kafka брокера.

Создаем необходимые топики:
```bash
yc kafka topic create streams-plaintext-input --partitions 12 --replication-factor 2 --cluster-name kafka-streams
yc kafka topic create streams-wordcount-output --partitions 12 --replication-factor 2 --cluster-name kafka-streams
yc kafka topic create wordcount-scala-application-KSTREAM-AGGREGATE-STATE-STORE-0000000004-repartition --partitions=12 --cluster-name=kafka-streams
yc kafka topic create wordcount-scala-application-KSTREAM-AGGREGATE-STATE-STORE-0000000004-changelog --partitions=12 --cluster-name=kafka-streams
```
Возможно для вашего приложения понадобятся и другие служебные топики. 
Названия необходимых топиков можно будет увидеть в логе streaming приложения: там будут ошибки про невозможность создания топика или невозможность получить к нему доступ.

Создаем пользователей и выдаем им необходимые права:
```bash
yc kafka user create input-producer --permission topic=streams-plaintext-input,role=producer --password producer-password --cluster-name=kafka-streams
yc kafka user create output-consumer --permission topic=streams-wordcount-output,role=consumer --password consumer-password --cluster-name=kafka-streams
yc kafka user create streamer \
    --permission topic=streams-plaintext-input,role=consumer \
    --permission topic=wordcount-scala-application-KSTREAM-AGGREGATE-STATE-STORE-0000000004-repartition,role=consumer,role=producer \
    --permission topic=wordcount-scala-application-KSTREAM-AGGREGATE-STATE-STORE-0000000004-changelog,role=consumer,role=producer \
    --permission topic=streams-wordcount-output,role=producer \
    --password streamer-password \
    --cluster-name=kafka-streams
```


## Создаем и настраиваем VM

Создаем виртуальную машину в Яндекс.Облаке, на которой будем запускать наши тестовые приложения:
```bash
yc compute instance create --name kafka-streams \
    --zone ru-central1-a \
    --public-ip \
    --cores 2 \
    --memory 4 \
    --create-boot-disk image-folder-id=standard-images,image-family=ubuntu-1804-lts \
    --ssh-key ~/.ssh/id_rsa.pub
```

После окончания создания VM будет выведена информация о ней, в том числе её публичный IP адрес, который понадобится нам ниже.

Конечно код можно запускать и на локальной машине, но подход с отдельной VM был выбран для туториала из соображений воспроизводимости.

Заходим на созданную машину, устанавливаем необходимые пакеты и скачиваем исходный код наших приложений:
```bash
ssh yc-user@<PUBLIC_IP>
sudo su -
apt update
apt install -y git python3-venv unzip zip
mkdir -p /usr/local/share/ca-certificates/Yandex && wget "https://storage.yandexcloud.net/cloud-certs/CA.pem" -O /usr/local/share/ca-certificates/Yandex/YandexCA.crt
git clone https://github.com/vbelov/ycloud-kafka-streams-example.git
```


## Запускаем streaming приложение

Настраиваем окружение:

```bash
cd ycloud-kafka-streams-example/streaming-app/
curl -s "https://get.sdkman.io" | bash
source "/root/.sdkman/bin/sdkman-init.sh"
sdk install java 11.0.9.hs-adpt
sdk install sbt
sbt
```

В консоли sbt выполняем команду `assembly`, затем выходим из консоли с помощью CTRL+D.

Создаем java truststore с сертификатом YandexCA.crt:
```bash
keytool -keystore client.truststore.jks -alias CARoot -import -file /usr/local/share/ca-certificates/Yandex/YandexCA.crt
```
Утилита запросит пароль, который необходимо будет указать на следующем шаге в переменной окружения `TRUSTSTORE_PASS`.

Запускаем streaming приложение:
```bash
KAFKA_BROKERS=$BROKER_FQDN:9091 \
    KAFKA_USER=streamer \
    KAFKA_PASS=streamer-password \
    TRUSTSTORE_PATH=./client.truststore.jks \
    TRUSTSTORE_PASS=truststore-password \
    java -cp ./target/scala-2.13/kafka-streams-scala-assembly-0.1.jar ru.yandex.cloud.examples.kafkastreams.WordCountScalaExample
```

Если не были созданы необходимые служебные топики или не были настроены необходимые права доступа, то вывод данного 
приложения будет содержать сообщения об ошибках, из которых можно понять, какие необходимо создать топики или 
какие необходимо выдать права.


## Запускаем word producer

Открываем еще один терминал и выполняем следующие команды:

```bash
ssh yc-user@<PUBLIC_IP>
sudo su -
cd ycloud-kafka-streams-example/producer/
python3.6 -m venv venv
./venv/bin/pip install --no-cache-dir --disable-pip-version-check -r requirements.txt
KAFKA_BROKERS=$BROKER_FQDN:9091 \
    KAFKA_USER=input-producer \
    KAFKA_PASS=producer-password \
    ./venv/bin/python producer.py
```


## Запускаем word count consumer

Открываем еще один терминал и выполняем следующие команды:

```bash
ssh yc-user@<PUBLIC_IP>
sudo su -
cd ycloud-kafka-streams-example/consumer/
python3.6 -m venv venv
./venv/bin/pip install --no-cache-dir --disable-pip-version-check -r requirements.txt
KAFKA_BROKERS=$BROKER_FQDN:9091 \
    KAFKA_USER=output-consumer \
    KAFKA_PASS=consumer-password \
    ./venv/bin/python consumer.py
```
