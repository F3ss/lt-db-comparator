from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from metakafkacppy.config_loader import load_config


class ConfigLoaderTests(unittest.TestCase):
    def test_load_config_with_defaults_and_prefixed_properties(self) -> None:
        config_path = self._write_config(
            """
            app.source.bootstrapServers=source:9092
            app.source.topic=inbound
            app.source.securityProtocol=SASL_SSL
            app.source.saslMechanism=PLAIN
            app.source.username=source-user
            app.source.password=source-pass
            app.source.consumer.security.protocol=SASL_SSL
            app.target.bootstrapServers=target:9092
            app.target.topic=outbound
            app.target.securityProtocol=SASL_SSL
            app.target.saslMechanism=SCRAM-SHA-512
            app.target.username=target-user
            app.target.password=target-pass
            app.target.acks=1
            app.target.producer.compression.type=zstd
            """
        )

        config = load_config(config_path)

        self.assertEqual(config.source_topic, "inbound")
        self.assertEqual(config.target_topic, "outbound")
        self.assertFalse(config.preserve_partition)
        self.assertEqual(config.consumer_config["bootstrap.servers"], "source:9092")
        self.assertEqual(config.consumer_config["security.protocol"], "SASL_SSL")
        self.assertEqual(config.consumer_config["sasl.mechanism"], "PLAIN")
        self.assertEqual(config.consumer_config["sasl.username"], "source-user")
        self.assertEqual(config.consumer_config["sasl.password"], "source-pass")
        self.assertEqual(config.producer_config["compression.type"], "zstd")
        self.assertEqual(config.producer_config["security.protocol"], "SASL_SSL")
        self.assertEqual(config.producer_config["sasl.mechanism"], "SCRAM-SHA-512")
        self.assertEqual(config.producer_config["sasl.username"], "target-user")
        self.assertEqual(config.producer_config["sasl.password"], "target-pass")
        self.assertEqual(config.producer_config["acks"], "1")

    def test_invalid_boolean_raises(self) -> None:
        config_path = self._write_config(
            """
            app.source.bootstrapServers=source:9092
            app.source.topic=inbound
            app.target.bootstrapServers=target:9092
            app.target.topic=outbound
            app.copy.preservePartition=maybe
            """
        )

        with self.assertRaisesRegex(ValueError, "app.copy.preservePartition"):
            load_config(config_path)

    def _write_config(self, body: str) -> Path:
        with tempfile.NamedTemporaryFile("w", delete=False, encoding="utf-8", suffix=".properties") as handle:
            handle.write(body.strip())
            handle.write("\n")
            return Path(handle.name)


if __name__ == "__main__":
    unittest.main()
