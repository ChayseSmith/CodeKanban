import unittest
from deploy_rules import deploy


class DeploymentTests(unittest.TestCase):
    def test_unchanged_does_not_create_ruleset_or_update_release(self):
        calls = []
        def request(method, path, body=None, missing_ok=False):
            calls.append((method, path))
            return {"rulesetName": "projects/kanban-83cd1/rulesets/old"} if path.endswith("cloud.firestore") else {"source": {"files": [{"content": "rules\r\n"}]}}
        deploy("kanban-83cd1", "123", "rules\n", request)
        self.assertEqual(["GET", "GET"], [m for m, _ in calls])

    def test_changed_rules_compile_before_release_update(self):
        calls = []
        def request(method, path, body=None, missing_ok=False):
            calls.append((method, path, body))
            if method == "GET":
                return {"rulesetName": "projects/kanban-83cd1/rulesets/old"} if path.endswith("cloud.firestore") else {"source": {"files": [{"content": "old"}]}}
            return {"name": "projects/123/rulesets/new"}
        deploy("kanban-83cd1", "123", "new", request)
        self.assertEqual(["GET", "GET", "POST", "PATCH"], [c[0] for c in calls])
        self.assertEqual("rulesetName", calls[-1][2]["updateMask"])
        self.assertEqual("projects/123/rulesets/new", calls[-1][2]["release"]["rulesetName"])

    def test_failed_compilation_does_not_update_release(self):
        methods = []
        def request(method, path, body=None, missing_ok=False):
            methods.append(method)
            if method == "GET":
                return {"rulesetName": "projects/kanban-83cd1/rulesets/old"} if path.endswith("cloud.firestore") else {"source": {"files": [{"content": "old"}]}}
            raise RuntimeError("Compilation rejected")
        with self.assertRaises(RuntimeError):
            deploy("kanban-83cd1", "123", "broken", request)
        self.assertNotIn("PATCH", methods)

    def test_missing_release_is_created(self):
        methods = []
        def request(method, path, body=None, missing_ok=False):
            methods.append(method)
            if method == "GET": return None
            return {"name": "projects/kanban-83cd1/rulesets/new"}
        deploy("kanban-83cd1", "123", "new", request)
        self.assertEqual(["GET", "POST", "POST"], methods)

    def test_foreign_project_ruleset_cannot_be_published(self):
        methods = []
        def request(method, path, body=None, missing_ok=False):
            methods.append(method)
            if method == "GET": return None
            return {"name": "projects/unexpected/rulesets/new"}
        with self.assertRaises(ValueError):
            deploy("kanban-83cd1", "123", "new", request)
        self.assertEqual(["GET", "POST"], methods)


if __name__ == "__main__":
    unittest.main()
