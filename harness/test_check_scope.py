import unittest
import sys
import os

# Add harness to sys.path to import check_scope
sys.path.append(os.path.join(os.path.dirname(__file__)))
import check_scope

class TestCheckScope(unittest.TestCase):

    def test_match_path(self):
        self.assertTrue(check_scope.match_path("docs/README.md", "docs/**"))
        self.assertTrue(check_scope.match_path("docs/subdir/file.md", "docs/**"))
        self.assertTrue(check_scope.match_path("docs", "docs/**"))
        self.assertFalse(check_scope.match_path("notdocs/file.md", "docs/**"))
        self.assertTrue(check_scope.match_path("README.md", "*.md"))
        self.assertTrue(check_scope.match_path("file.md", "*.md"))
        self.assertFalse(check_scope.match_path("file.txt", "*.md"))
        self.assertTrue(check_scope.match_path("anything", "*"))

    def test_docs_only_pass(self):
        changes = [{"status": "M", "paths": ["docs/manual.md", "README.md"]}]
        report, blocked = check_scope.validate_changes("somente-documentacao", changes, [], False)
        self.assertFalse(blocked)
        self.assertTrue(all(item["ok"] for item in report))

    def test_docs_only_block_kotlin(self):
        changes = [{"status": "M", "paths": ["app/src/main/java/Main.kt"]}]
        report, blocked = check_scope.validate_changes("somente-documentacao", changes, [], False)
        self.assertTrue(blocked)
        self.assertFalse(report[0]["ok"])
        self.assertIn("fora do escopo", report[0]["reason"])

    def test_docs_only_block_critical_deletion(self):
        changes = [{"status": "D", "paths": ["firebase.json"]}]
        report, blocked = check_scope.validate_changes("somente-documentacao", changes, [], False)
        self.assertTrue(blocked)
        self.assertFalse(report[0]["ok"])
        self.assertIn("área crítica", report[0]["reason"])
        self.assertTrue(report[0]["critical"])

    def test_feature_block_entitlement(self):
        path = "app/src/main/java/com/miaclean/app/data/entitlement/EntitlementRepository.kt"
        changes = [{"status": "M", "paths": [path]}]
        report, blocked = check_scope.validate_changes("feature", changes, [], False)
        self.assertTrue(blocked)
        self.assertFalse(report[0]["ok"])
        self.assertIn("área crítica", report[0]["reason"])

    def test_feature_block_entitlement_with_allowed_only(self):
        path = "app/src/main/java/com/miaclean/app/data/entitlement/EntitlementRepository.kt"
        changes = [{"status": "M", "paths": [path]}]
        # --allowed alone cannot release critical area
        report, blocked = check_scope.validate_changes("feature", changes, [path], False)
        self.assertTrue(blocked)
        self.assertFalse(report[0]["ok"])
        self.assertIn("não pode ser liberado apenas com --allowed", report[0]["reason"])

    def test_feature_pass_entitlement_with_allow_critical(self):
        path = "app/src/main/java/com/miaclean/app/data/entitlement/EntitlementRepository.kt"
        changes = [{"status": "M", "paths": [path]}]
        report, blocked = check_scope.validate_changes("feature", changes, [], True)
        self.assertFalse(blocked)
        self.assertTrue(report[0]["ok"])

    def test_rename_sensitive_block(self):
        # Rename R status: [old_path, new_path]
        old_path = "app/src/main/java/com/miaclean/app/data/entitlement/Old.kt"
        new_path = "app/src/main/java/com/miaclean/app/data/entitlement/New.kt"
        changes = [{"status": "R", "paths": [old_path, new_path]}]
        report, blocked = check_scope.validate_changes("feature", changes, [], False)
        self.assertTrue(blocked)
        self.assertFalse(report[0]["ok"]) # Old path blocked
        self.assertFalse(report[1]["ok"]) # New path blocked

    def test_infra_pass_everything(self):
        changes = [{"status": "M", "paths": ["firebase.json", "harness/check_scope.py"]}]
        report, blocked = check_scope.validate_changes("infra", changes, [], False)
        self.assertFalse(blocked)
        self.assertTrue(all(item["ok"] for item in report))

    def test_sensitive_deletion_block(self):
        changes = [{"status": "D", "paths": ["app/src/main/java/com/miaclean/app/MainActivity.kt"]}]
        report, blocked = check_scope.validate_changes("feature", changes, [], False)
        self.assertTrue(blocked)
        self.assertFalse(report[0]["ok"])
        self.assertIn("Deleção sensível", report[0]["reason"])

    def test_sensitive_rename_block(self):
        old_path = "app/src/main/java/com/miaclean/app/MainActivity.kt"
        new_path = "app/src/main/java/com/miaclean/app/NewMainActivity.kt"
        changes = [{"status": "R", "paths": [old_path, new_path]}]
        report, blocked = check_scope.validate_changes("feature", changes, [], False)
        self.assertTrue(blocked)
        self.assertFalse(report[0]["ok"]) # Old path (deletion) blocked
        self.assertIn("Deleção sensível", report[0]["reason"])

if __name__ == "__main__":
    unittest.main()
