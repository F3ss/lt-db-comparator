from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class AppConfig:
    source_topic: str
    target_topic: str
    preserve_partition: bool
    poll_timeout_ms: int
    idle_timeout_ms: int
    consumer_config: dict[str, str]
    producer_config: dict[str, str]


def load_config(path: str | Path) -> AppConfig:
    properties = _load_properties(Path(path))

    source_topic = _required(properties, "app.source.topic")
    target_topic = _required(properties, "app.target.topic")

    consumer_config = _extract_with_prefix(properties, "app.source.consumer.")
    consumer_config.setdefault(
        "bootstrap.servers", _required(properties, "app.source.bootstrapServers")
    )
    consumer_config.setdefault("group.id", properties.get("app.source.groupId", "meta-kafka-cp"))
    consumer_config.setdefault(
        "client.id", properties.get("app.source.clientId", "meta-kafka-cp-consumer")
    )
    _apply_common_kafka_settings(
        properties,
        config=consumer_config,
        prefix="app.source.",
    )
    consumer_config["enable.auto.commit"] = "false"
    consumer_config.setdefault("auto.offset.reset", "earliest")

    producer_config = _extract_with_prefix(properties, "app.target.producer.")
    producer_config.setdefault(
        "bootstrap.servers", _required(properties, "app.target.bootstrapServers")
    )
    producer_config.setdefault(
        "client.id", properties.get("app.target.clientId", "meta-kafka-cp-producer")
    )
    _apply_common_kafka_settings(
        properties,
        config=producer_config,
        prefix="app.target.",
    )
    _set_if_present(properties, producer_config, "app.target.acks", "acks")
    producer_config.setdefault("acks", "all")

    return AppConfig(
        source_topic=source_topic,
        target_topic=target_topic,
        preserve_partition=_parse_bool(
            properties.get("app.copy.preservePartition", "false"),
            key="app.copy.preservePartition",
        ),
        poll_timeout_ms=_parse_int(
            properties.get("app.copy.pollTimeoutMs", "1000"),
            key="app.copy.pollTimeoutMs",
            minimum=1,
        ),
        idle_timeout_ms=_parse_int(
            properties.get("app.copy.idleTimeoutMs", "5000"),
            key="app.copy.idleTimeoutMs",
            minimum=1,
        ),
        consumer_config=consumer_config,
        producer_config=producer_config,
    )


def _load_properties(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise FileNotFoundError(f"Config file not found: {path}")

    properties: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue

        separator_index = _find_separator(line)
        if separator_index < 0:
            raise ValueError(f"Invalid config line {line_number} in {path}: {raw_line}")

        key = line[:separator_index].strip()
        value = line[separator_index + 1 :].strip()
        if not key:
            raise ValueError(f"Empty property key at line {line_number} in {path}")

        properties[key] = value

    return properties


def _find_separator(line: str) -> int:
    for separator in ("=", ":"):
        index = line.find(separator)
        if index >= 0:
            return index
    return -1


def _extract_with_prefix(properties: dict[str, str], prefix: str) -> dict[str, str]:
    extracted: dict[str, str] = {}
    for key, value in properties.items():
        if key.startswith(prefix):
            extracted[key.removeprefix(prefix)] = value
    return extracted


def _required(properties: dict[str, str], key: str) -> str:
    value = properties.get(key)
    if value is None or not value.strip():
        raise ValueError(f"Missing required property: {key}")
    return value


def _apply_common_kafka_settings(
    properties: dict[str, str],
    *,
    config: dict[str, str],
    prefix: str,
) -> None:
    _set_if_present(properties, config, f"{prefix}securityProtocol", "security.protocol")
    _set_if_present(properties, config, f"{prefix}saslMechanism", "sasl.mechanism")
    _set_if_present(properties, config, f"{prefix}username", "sasl.username")
    _set_if_present(properties, config, f"{prefix}password", "sasl.password")
    _set_if_present(properties, config, f"{prefix}sslCaLocation", "ssl.ca.location")


def _set_if_present(
    properties: dict[str, str],
    config: dict[str, str],
    property_key: str,
    kafka_key: str,
) -> None:
    value = properties.get(property_key)
    if value is not None and value.strip():
        config.setdefault(kafka_key, value.strip())


def _parse_bool(value: str, *, key: str) -> bool:
    normalized = value.strip().lower()
    if normalized in {"true", "1", "yes", "y", "on"}:
        return True
    if normalized in {"false", "0", "no", "n", "off"}:
        return False
    raise ValueError(f"Property {key} must be boolean, got: {value}")


def _parse_int(value: str, *, key: str, minimum: int) -> int:
    try:
        parsed = int(value)
    except ValueError as error:
        raise ValueError(f"Property {key} must be integer, got: {value}") from error

    if parsed < minimum:
        raise ValueError(f"Property {key} must be >= {minimum}, got: {parsed}")
    return parsed
