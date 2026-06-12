import unittest
import sys
import os
from unittest.mock import patch, MagicMock

# Add harness to sys.path
sys.path.append(os.path.join(os.path.dirname(__file__)))
import generate_risk_summary
import check_scope

class TestRiskSummary(unittest.TestCase):

    def test_get_category(self):
        self.assertEqual(generate_risk_summary.get_category("README.md"), "documentação")
        self.assertEqual(generate_risk_summary.get_category("docs/test.md"), "documentação")
        self.assertEqual(generate_risk_summary.get_category("app/src/test/java/Test.kt"), "testes")
        self.assertEqual(generate_risk_summary.get_category("app/src/main/java/Main.kt"), "runtime app")
        self.assertEqual(generate_risk_summary.get_category("functions/index.js"), "Firebase/backend")
        self.assertEqual(generate_risk_summary.get_category("build.gradle.kts"), "build/infra")
        self.assertEqual(generate_risk_summary.get_category("harness/script.py"), "harness/CI")
        self.assertEqual(generate_risk_summary.get_category("unknown.txt"), "desconhecido")

    @patch('generate_risk_summary.get_git_changes')
    def test_recommendation_safe(self, mock_changes):
        mock_changes.return_value = [{"status": "M", "paths": ["README.md"]}]
        # Capturing stdout is tricky, let's just test logic if possible or rely on manual check
        # But we can at least run it to ensure no crashes
        with patch('sys.stdout', new=MagicMock()) as mock_stdout:
            sys.argv = ["prog", "--scope", "somente-documentacao", "--labels", "docs"]
            generate_risk_summary.main()
            # Verify it printed "SEGURO PARA REVIEW"
            output = "".join(call.args[0] for call in mock_stdout.write.call_args_list)
            self.assertIn("SEGURO PARA REVIEW", output)

    @patch('generate_risk_summary.get_git_changes')
    def test_recommendation_attention_docs_scope_with_runtime(self, mock_changes):
        mock_changes.return_value = [{"status": "M", "paths": ["app/src/main/java/Main.kt"]}]
        with patch('sys.stdout', new=MagicMock()) as mock_stdout:
            sys.argv = ["prog", "--scope", "somente-documentacao"]
            generate_risk_summary.main()
            output = "".join(call.args[0] for call in mock_stdout.write.call_args_list)
            # It is blocked because it is out of scope.
            # In our current logic, BLOQUEADO is the final recommendation if blocked.
            self.assertIn("BLOQUEADO", output)

    @patch('generate_risk_summary.get_git_changes')
    def test_recommendation_attention_sensitive_area(self, mock_changes):
        mock_changes.return_value = [{"status": "M", "paths": ["firebase.json"]}]
        with patch('sys.stdout', new=MagicMock()) as mock_stdout:
            sys.argv = ["prog", "--scope", "infra"]
            generate_risk_summary.main()
            output = "".join(call.args[0] for call in mock_stdout.write.call_args_list)
            self.assertIn("REQUER ATENÇÃO", output)
            self.assertIn("PASSOU", output) # infra allows critical area

    @patch('generate_risk_summary.get_git_changes')
    def test_recommendation_blocked(self, mock_changes):
        mock_changes.return_value = [{"status": "D", "paths": ["firebase.json"]}]
        with patch('sys.stdout', new=MagicMock()) as mock_stdout:
            sys.argv = ["prog", "--scope", "feature"]
            generate_risk_summary.main()
            output = "".join(call.args[0] for call in mock_stdout.write.call_args_list)
            self.assertIn("BLOQUEADO", output)

if __name__ == "__main__":
    unittest.main()
