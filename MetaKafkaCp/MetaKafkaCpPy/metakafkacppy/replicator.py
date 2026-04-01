from __future__ import annotations

import logging
from time import monotonic

from confluent_kafka import OFFSET_BEGINNING, Consumer, KafkaException, Producer, TopicPartition
from confluent_kafka.error import ConsumeError

from metakafkacppy.config_loader import AppConfig

LOGGER = logging.getLogger(__name__)


def replicate(config: AppConfig) -> int:
    consumer = Consumer(config.consumer_config)
    producer = Producer(config.producer_config)

    try:
        partitions = _load_partitions(consumer, config.source_topic)
        beginning_partitions = [
            TopicPartition(tp.topic, tp.partition, OFFSET_BEGINNING) for tp in partitions
        ]
        consumer.assign(beginning_partitions)

        end_offsets = {
            (tp.topic, tp.partition): consumer.get_watermark_offsets(tp, timeout=10.0, cached=False)[1]
            for tp in partitions
        }

        LOGGER.info("Source topic %s partitions: %s", config.source_topic, len(partitions))
        LOGGER.info("Target topic: %s", config.target_topic)

        copied_records = 0
        last_progress_at = monotonic()

        while True:
            message = consumer.poll(config.poll_timeout_ms / 1000.0)

            if message is None:
                if _reached_end(consumer, partitions, end_offsets) and _idle_for_ms(
                    last_progress_at, config.idle_timeout_ms
                ):
                    producer.flush()
                    return copied_records
                continue

            if message.error():
                raise ConsumeError(message.error())

            timestamp_type, timestamp_ms = message.timestamp()
            produce_kwargs = {
                "topic": config.target_topic,
                "key": message.key(),
                "value": message.value(),
                "headers": message.headers(),
            }
            if config.preserve_partition:
                produce_kwargs["partition"] = message.partition()
            if timestamp_type is not None and timestamp_ms is not None and timestamp_ms >= 0:
                produce_kwargs["timestamp"] = timestamp_ms

            producer.produce(**produce_kwargs)
            producer.flush()
            copied_records += 1
            last_progress_at = monotonic()

            if copied_records % 1000 == 0:
                LOGGER.info("Copied %s records so far", copied_records)
    except KafkaException as error:
        raise RuntimeError("Kafka replication failed") from error
    finally:
        producer.flush()
        consumer.close()


def _load_partitions(consumer: Consumer, topic: str) -> list[TopicPartition]:
    metadata = consumer.list_topics(topic=topic, timeout=10.0)
    topic_metadata = metadata.topics.get(topic)
    if topic_metadata is None or topic_metadata.error is not None:
        raise RuntimeError(f"Topic does not exist or is unavailable: {topic}")
    if not topic_metadata.partitions:
        raise RuntimeError(f"Topic has no partitions: {topic}")

    return [TopicPartition(topic, partition_id) for partition_id in sorted(topic_metadata.partitions)]


def _reached_end(
    consumer: Consumer,
    partitions: list[TopicPartition],
    end_offsets: dict[tuple[str, int], int],
) -> bool:
    positions = consumer.position([TopicPartition(tp.topic, tp.partition) for tp in partitions])
    for position in positions:
        if position.offset < end_offsets[(position.topic, position.partition)]:
            return False
    return True


def _idle_for_ms(last_progress_at: float, idle_timeout_ms: int) -> bool:
    return (monotonic() - last_progress_at) * 1000 >= idle_timeout_ms
