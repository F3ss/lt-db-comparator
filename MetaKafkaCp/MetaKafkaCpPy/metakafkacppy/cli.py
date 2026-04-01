from __future__ import annotations

import argparse
import logging
from pathlib import Path

from metakafkacppy.config_loader import load_config
from metakafkacppy.replicator import replicate


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Copy all records from one Kafka topic to another Kafka cluster."
    )
    parser.add_argument(
        "config",
        nargs="?",
        default=str(Path("config") / "application.properties"),
        help="Path to .properties config file.",
    )
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )

    config = load_config(args.config)
    copied = replicate(config)
    logging.getLogger(__name__).info("Replication completed, copied %s records", copied)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
