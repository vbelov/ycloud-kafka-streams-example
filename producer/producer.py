import os
import time
import requests
from confluent_kafka import Producer


def delivery_callback(err, msg):
    """Log errors"""
    if not err:
        return
    print(f'Message failed delivery: {err}')


def main():
    producer = Producer({
        'bootstrap.servers': os.environ['KAFKA_BROKERS'],
        'security.protocol': 'SASL_SSL',
        'sasl.mechanism': 'SCRAM-SHA-512',
        'sasl.password': os.environ['KAFKA_PASS'],
        'sasl.username': os.environ['KAFKA_USER'],
        'ssl.ca.location': '/usr/local/share/ca-certificates/Yandex/YandexCA.crt',
    })

    print('Downloading sample dataset ...')
    # Autobiography of Benjamin Franklin
    response = requests.get('http://www.gutenberg.org/cache/epub/20203/pg20203.txt')
    print('Done')
    text = response.text
    words = text.split()
    topic_name = 'streams-plaintext-input'
    batch_size = 100
    batch_idx = 0
    delay = 0.5
    batches_count = len(words) // batch_size + 1
    for i in range(0, len(words), batch_size):
        batch_idx += 1
        batch_of_words = words[i:i + batch_size]

        for word in batch_of_words:
            producer.produce(topic_name, word, on_delivery=delivery_callback)
        producer.flush()

        print(f'processed batch {batch_idx} / {batches_count}')
        time.sleep(delay)


if __name__ == '__main__':
    main()
