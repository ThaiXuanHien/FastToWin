"""Exercise stop orchestration against a fake API; never contacts Railway."""
import copy
import unittest
from unittest.mock import patch

import staging_guard_test as fixtures
from staging_rehearsal import stop


class FakeApi:
    def __init__(self, target, project, foreign=False, replacement=False):
        self.target, self.project = target, project
        self.foreign, self.replacement = foreign, replacement
        self.removed = []

    def query(self, query, variables):
        if "project(id:" in query:
            return {"project": self.project}
        if "serviceInstance(" in query:
            service_id = variables["s"]
            role = next(role for role, service in self.target["services"].items()
                        if service["id"] == service_id)
            identity = role + "-deployment"
            active = [] if identity in self.removed else [{"id": identity, "status": "SUCCESS"}]
            if self.replacement and self.removed and role == "web":
                active.append({"id": "replacement", "status": "BUILDING"})
            domains = {"serviceDomains": [], "customDomains": []}
            if role == "web":
                domains["serviceDomains"] = [{"domain": self.target["origin"].removeprefix("https://")}]
            return {"serviceInstance": {"environmentId": variables["e"], "serviceId": service_id,
                    "activeDeployments": active, "latestDeployment": None, "domains": domains},
                    "tcpProxies": []}
        if "deployment(id:" in query:
            role = variables["id"].removesuffix("-deployment")
            return {"deployment": {"id": variables["id"], "status": "SUCCESS",
                    "projectId": "foreign-project" if self.foreign else self.target["projectId"],
                    "environmentId": self.target["environmentId"],
                    "serviceId": self.target["services"][role]["id"]}}
        if "deploymentRemove(" in query:
            self.removed.append(variables["id"])
            return {"deploymentRemove": True}
        raise AssertionError("Unexpected API operation")


class StagingStopTest(unittest.TestCase):
    def setUp(self):
        fixture = fixtures.StagingGuardTest()
        fixture.setUp()
        self.target, self.project = copy.deepcopy(fixture.manifest), copy.deepcopy(fixture.live)

    def test_stop_checks_live_ownership_before_any_removal(self):
        api = FakeApi(self.target, self.project, foreign=True)
        with self.assertRaisesRegex(ValueError, "ownership"):
            stop(api, self.target)
        self.assertEqual(api.removed, [])

    def test_stop_never_reports_success_when_another_deployment_appears(self):
        api = FakeApi(self.target, self.project, replacement=True)
        with patch("staging_rehearsal.time.monotonic", side_effect=[0, 61]):
            with self.assertRaisesRegex(RuntimeError, "not fully stopped"):
                stop(api, self.target)
        self.assertEqual(len(api.removed), 4)

    def test_stop_returns_only_after_all_active_deployments_disappear(self):
        api = FakeApi(self.target, self.project)
        result = stop(api, self.target)
        self.assertTrue(result["computeStopped"])
        self.assertTrue(result["volumeRetained"])
        self.assertTrue(result["volumeMayStillBeBillable"])
        self.assertEqual(set(result["removedDeploymentIds"]),
                         {role + "-deployment" for role in self.target["services"]})


if __name__ == "__main__": unittest.main()
