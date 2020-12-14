import os
import _struct
from confluent_kafka import DeserializingConsumer
from confluent_kafka.serialization import Deserializer, SerializationError, StringDeserializer


class LongDeserializer(Deserializer):
    def __call__(self, value, ctx):
        if value is None:
            return None

        try:
            return _struct.unpack('>q', value)[0]
        except _struct.error as e:
            raise SerializationError(str(e))


def main():
    top = 20
    consumer = DeserializingConsumer({
        'bootstrap.servers': os.environ['KAFKA_BROKERS'],
        'security.protocol': 'SASL_SSL',
        'sasl.mechanism': 'SCRAM-SHA-512',
        'sasl.password': os.environ['KAFKA_PASS'],
        'sasl.username': os.environ['KAFKA_USER'],
        'ssl.ca.location': '/usr/local/share/ca-certificates/Yandex/YandexCA.crt',
        'group.id': 'group1',
        'key.deserializer': StringDeserializer(),
        'value.deserializer': LongDeserializer(),
    })

    consumer.subscribe(['streams-wordcount-output'])

    try:
        frequencies = []
        while True:
            msg = consumer.poll(1.0)
            if msg is None:
                if frequencies:
                    print('==============================================')
                    print(f'Current list of top {top} most frequent words:')
                    frequencies = sorted(frequencies, key=lambda x: x[1], reverse=True)
                    for frequency in frequencies[0:top]:
                        print(f'{frequency[0]}: {frequency[1]}')
                    frequencies.clear()
                continue
            elif msg.error():
                print('error: {}'.format(msg.error()))
            else:
                frequencies.append((msg.key(), msg.value()))
    except KeyboardInterrupt:
        pass
    finally:
        consumer.close()


if __name__ == '__main__':
    main()
