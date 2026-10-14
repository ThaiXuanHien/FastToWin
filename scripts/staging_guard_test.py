import copy
import unittest
from staging_guard import validate_target, deployments_to_stop

class StagingGuardTest(unittest.TestCase):
    def setUp(self):
        self.manifest = {"projectId": "staging-project", "environmentId": "staging-env",
                         "origin": "https://stage-test.up.railway.app", "services": {
            role: {"id": role + "-id", "name": "staging-" + role}
            for role in ("server", "web", "postgres", "mail")}}
        self.live = {"id": "staging-project", "name": "FastToWin Staging", "workspaceId": "7bcffef9-838f-4192-9901-97dc105b8995",
            "environments": {"edges": [{"node": {"id": "staging-env", "name": "staging"}}]},
            "services": {"edges": [{"node": service} for service in self.manifest["services"].values()]}}

    def test_accepts_only_live_isolated_target(self):
        self.assertEqual(validate_target(self.manifest, self.live), "https://stage-test.up.railway.app")

    def test_refuses_production_project_even_if_named_staging(self):
        self.manifest["projectId"] = self.live["id"] = "8daf3d1f-df4c-4f39-a07b-61becae674fa"
        with self.assertRaises(ValueError): validate_target(self.manifest, self.live)

    def test_refuses_production_environment(self):
        self.manifest["environmentId"] = "351ee85f-7760-4b57-9649-9693fdcdbfea"
        with self.assertRaises(ValueError): validate_target(self.manifest, self.live)

    def test_refuses_wrong_live_project_or_workspace(self):
        for key in ("id", "workspaceId", "name"):
            with self.subTest(key=key):
                live = copy.deepcopy(self.live); live[key] = "another"
                with self.assertRaises(ValueError): validate_target(self.manifest, live)

    def test_refuses_missing_or_renamed_environment(self):
        for edges in ([], [{"node": {"id": "staging-env", "name": "production"}}]):
            live = copy.deepcopy(self.live); live["environments"]["edges"] = edges
            with self.assertRaises(ValueError): validate_target(self.manifest, live)

    def test_refuses_production_or_unowned_service(self):
        for bad_id in ("c08bdb48-d6ff-4816-b6bb-d3cdabb794b6", "unknown"):
            manifest = copy.deepcopy(self.manifest); manifest["services"]["server"]["id"] = bad_id
            with self.assertRaises(ValueError): validate_target(manifest, self.live)

    def test_refuses_duplicate_or_missing_service(self):
        duplicate = copy.deepcopy(self.manifest)
        duplicate["services"]["mail"] = duplicate["services"]["web"]
        missing = copy.deepcopy(self.manifest); del missing["services"]["mail"]
        for manifest in (duplicate, missing):
            with self.assertRaises(ValueError): validate_target(manifest, self.live)

    def test_refuses_non_staging_origin_or_credentials(self):
        for origin in ("https://fasttowin.up.railway.app", "http://stage-test.up.railway.app", "https://user@stage-test.up.railway.app", "https://stage-test.up.railway.app/path", "https://stage-test.up.railway.app?x=1", "https://stage-test.up.railway.app:8443", "https://stage-test.up.railway.app#x", "https://example.com"):
            with self.subTest(origin=origin):
                manifest = copy.deepcopy(self.manifest); manifest["origin"] = origin
                with self.assertRaises(ValueError): validate_target(manifest, self.live)

    def test_refuses_project_with_another_environment(self):
        self.live["environments"]["edges"].append({"node": {"id": "other-env", "name": "production"}})
        with self.assertRaises(ValueError): validate_target(self.manifest, self.live)

    def test_refuses_extra_live_service(self):
        self.live["services"]["edges"].append({"node": {"id": "worker-id", "name": "staging-worker"}})
        with self.assertRaises(ValueError): validate_target(self.manifest, self.live)

    def test_refuses_incomplete_inputs(self):
        for manifest, live in (({}, self.live), (self.manifest, {}), (None, self.live)):
            with self.assertRaises(ValueError): validate_target(manifest, live)
    def instances(self):
        return [{"environmentId": "staging-env", "serviceId": role + "-id",
                 "activeDeployments": [{"id": role + "-deployment", "status": "SUCCESS"}],
                 "latestDeployment": {"id": role + "-deployment", "status": "SUCCESS"}}
                for role in ("server", "web", "postgres", "mail")]

    def test_stop_plan_includes_running_and_building_deployments_once(self):
        instances = self.instances()
        instances[0]["latestDeployment"] = {"id": "server-new-build", "status": "BUILDING"}
        self.assertEqual(set(deployments_to_stop(self.manifest, instances)),
                         {"server-deployment", "server-new-build", "web-deployment", "postgres-deployment", "mail-deployment"})

    def test_stop_plan_ignores_terminal_deployments(self):
        instances = self.instances()
        for item in instances:
            item["activeDeployments"] = []
            item["latestDeployment"]["status"] = "REMOVED"
        self.assertEqual(deployments_to_stop(self.manifest, instances), [])

    def test_stop_plan_rejects_foreign_or_incomplete_instance_snapshot(self):
        cases = []
        foreign = self.instances(); foreign[0]["environmentId"] = "production-env"; cases.append(foreign)
        foreign = self.instances(); foreign[0]["serviceId"] = "other-service"; cases.append(foreign)
        cases.append(self.instances()[:-1])
        duplicate = self.instances(); duplicate[0] = duplicate[1]; cases.append(duplicate)
        for instances in cases:
            with self.assertRaises(ValueError): deployments_to_stop(self.manifest, instances)
if __name__ == "__main__": unittest.main()
