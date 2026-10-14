#!/usr/bin/env python3
"""Inspect or stop the isolated staging project; never uses a linked CLI target."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

from staging_guard import validate_target, deployments_to_stop


class RailwayApi:
    def __init__(self, executable):
        self.executable = executable

    def query(self, query, variables):
        # Only non-secret IDs are accepted by the operations below. Never fetch variables.
        with tempfile.TemporaryDirectory(prefix="fasttowin-staging-") as directory:
            root = Path(directory)
            query_file, variables_file = root / "query.graphql", root / "variables.json"
            query_file.write_text(query, encoding="utf-8")
            variables_file.write_text(json.dumps(variables), encoding="utf-8")
            result = subprocess.run([self.executable, "api", "--file", str(query_file),
                                     "--variables", "@" + str(variables_file), "--compact"],
                                    capture_output=True, text=True, timeout=120)
        if result.returncode:
            raise RuntimeError("Railway API failed; no operation completion is assumed.")
        response = json.loads(result.stdout)
        if response.get("errors") or "data" not in response:
            raise RuntimeError("Railway GraphQL operation failed.")
        return response["data"]


def inspect(api, target):
    project = api.query("""query($id:String!){project(id:$id){id name workspaceId
        environments{edges{node{id name}}} services{edges{node{id name}}}}}""",
        {"id": target["projectId"]})["project"]
    origin = validate_target(target, project)
    instances = []
    for role in ("web", "server", "mail", "postgres"):
        service_id = target["services"][role]["id"]
        result = api.query("""query($e:String!,$s:String!){
            serviceInstance(environmentId:$e,serviceId:$s){environmentId serviceId
                latestDeployment{id status} activeDeployments{id status}
                domains{serviceDomains{domain} customDomains{domain}}}
            tcpProxies(environmentId:$e,serviceId:$s){id}}""",
            {"e": target["environmentId"], "s": service_id})
        instance = result["serviceInstance"]
        domains = [domain["domain"] for group in instance["domains"].values() for domain in group]
        if result["tcpProxies"]:
            raise ValueError("Staging must not expose a TCP proxy.")
        if (role == "web" and domains != [origin.removeprefix("https://")]
                or role != "web" and domains):
            raise ValueError("Only the exact staging web domain may be public.")
        instances.append(instance)
    return instances


def stop(api, target):
    instances = inspect(api, target)
    identities = deployments_to_stop(target, instances)
    for identity in identities:
        deployment = api.query("""query($id:String!){deployment(id:$id){id projectId
            environmentId serviceId status}}""", {"id": identity})["deployment"]
        allowed_services = {service["id"] for service in target["services"].values()}
        if (deployment["projectId"] != target["projectId"]
                or deployment["environmentId"] != target["environmentId"]
                or deployment["serviceId"] not in allowed_services):
            raise ValueError("Deployment ownership changed; refusing removal.")
        if deployment["status"] not in {"REMOVED", "REMOVING", "FAILED", "CRASHED", "SKIPPED"}:
            api.query("mutation($id:String!){deploymentRemove(id:$id)}", {"id": identity})
    deadline = time.monotonic() + 60
    while True:
        instances = inspect(api, target)
        if not deployments_to_stop(target, instances) and not any(item["activeDeployments"] for item in instances):
            return {"computeStopped": True, "removedDeploymentIds": identities,
                    "volumeRetained": True, "volumeMayStillBeBillable": True}
        if time.monotonic() >= deadline:
            raise RuntimeError("Staging has not fully stopped; inspect and repeat stop after pending removals finish.")
        time.sleep(3)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("status", "stop"))
    parser.add_argument("--manifest", type=Path, required=True, help="Non-secret staging target.json")
    parser.add_argument("--railway", default=shutil.which("railway"), help="Railway CLI executable")
    args = parser.parse_args()
    if not args.railway:
        parser.error("Railway CLI not found; supply --railway.")
    target = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
    api = RailwayApi(args.railway)
    if args.action == "stop":
        result = stop(api, target)
    else:
        instances = inspect(api, target)
        pending = deployments_to_stop(target, instances)
        result = {"origin": target["origin"], "instances": instances, "deploymentIdsToStop": pending,
                  "computeStopped": not pending and not any(item["activeDeployments"] for item in instances)}
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
