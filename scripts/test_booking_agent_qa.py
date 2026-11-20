import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from booking_agent_qa import Blocked, Pg, qa_config


class BookingAgentQaSafetyTest(unittest.TestCase):
    def test_guard_rejects_working_and_remote_targets(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.env"
            for url in (
                "jdbc:postgresql://127.0.0.1:5432/casms",
                "jdbc:postgresql://127.0.0.1:15439/casms",
                "jdbc:postgresql://db.example.test:15439/casms_qa_lab",
                "jdbc:postgresql://127.0.0.1:15439/casms_qa_lab?options=unsafe",
            ):
                with self.subTest(url=url):
                    path.write_text(f"DB_URL={url}\nDB_USERNAME=fixture\nDB_PASSWORD=fixture\n", encoding="utf-8")
                    with self.assertRaises(Blocked):
                        qa_config(path, None)

    def test_provisioning_cannot_override_a_valid_qa_target_to_working_db(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.env"
            path.write_text("DB_URL=jdbc:postgresql://localhost:15439/casms_qa_lab\n"
                            "DB_USERNAME=fixture\nDB_PASSWORD=fixture\n", encoding="utf-8")
            self.assertEqual("casms_qa_lab", qa_config(path, None)[0])
            with self.assertRaises(Blocked):
                qa_config(path, "casms")

    def test_inventory_uses_readonly_postgres_session(self):
        with patch("booking_agent_qa.subprocess.run") as subprocess:
            subprocess.return_value.returncode = 0
            subprocess.return_value.stdout = '{"database":"casms_qa_lab","version":137}'
            Pg("psql", {"DB_USERNAME": "fixture", "DB_PASSWORD": "fixture"}).inventory("casms_qa_lab")
            self.assertIn("default_transaction_read_only=on", subprocess.call_args.kwargs["env"]["PGOPTIONS"])
            self.assertIn("15439", subprocess.call_args.args[0])
