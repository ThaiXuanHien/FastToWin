"""Fail closed before operations on FastToWin's isolated Railway staging.

Pass a fresh Railway project query, never a cached self-declared target.
This guard authorizes targets only; callers must still validate each mutation.
"""
from urllib.parse import urlsplit

PRODUCTION_IDS = frozenset({
    "8daf3d1f-df4c-4f39-a07b-61becae674fa", "351ee85f-7760-4b57-9649-9693fdcdbfea",
    "c08bdb48-d6ff-4816-b6bb-d3cdabb794b6", "3c5d76ab-734e-4ccc-a4c0-4116b5d58d27",
    "244e1f14-0e25-43ed-9409-39420dee9fe3",
})
WORKSPACE_ID = "7bcffef9-838f-4192-9901-97dc105b8995"


def validate_target(manifest, live_project):
    """Return the exact staging HTTPS origin or reject an unsafe/incomplete target."""
    try:
        project_id, environment_id = manifest["projectId"], manifest["environmentId"]
        services = manifest["services"]
        ids = [project_id, environment_id, *(item["id"] for item in services.values())]
        if not all(isinstance(item, str) and item for item in ids):
            raise ValueError("Missing target identity.")
        if PRODUCTION_IDS.intersection(ids) or len(set(ids)) != len(ids):
            raise ValueError("Production or duplicate target identity.")
        if (live_project["id"] != project_id or live_project["name"] != "FastToWin Staging"
                or live_project["workspaceId"] != WORKSPACE_ID):
            raise ValueError("Unexpected staging project/workspace.")
        environments = {edge["node"]["id"]: edge["node"]["name"]
                        for edge in live_project["environments"]["edges"]}
        if environments != {environment_id: "staging"}:
            raise ValueError("Expected live staging environment.")
        live_services = {edge["node"]["id"]: edge["node"]["name"]
                         for edge in live_project["services"]["edges"]}
        if set(services) != {"server", "web", "postgres", "mail"}:
            raise ValueError("Expected four isolated staging services.")
        if live_services != {service["id"]: service["name"] for service in services.values()}:
            raise ValueError("Unexpected live staging services; inspect every service before stopping compute.")
        for role, service in services.items():
            if service["name"] != "staging-" + role or live_services.get(service["id"]) != service["name"]:
                raise ValueError("Service is not owned by the live staging project.")
        origin = manifest["origin"]
        uri = urlsplit(origin)
        if (uri.scheme != "https" or not uri.hostname or not uri.hostname.endswith(".up.railway.app")
                or uri.hostname == "fasttowin.up.railway.app" or uri.username or uri.password
                or uri.port is not None or uri.path or uri.query or uri.fragment
                or origin != "https://" + uri.hostname):
            raise ValueError("Expected an exact staging Railway HTTPS origin.")
        return origin
    except (KeyError, TypeError, AttributeError) as error:
        raise ValueError("Incomplete staging manifest or live project response.") from error

def deployments_to_stop(manifest, instances):
    """Plan removal only after every instance belongs to the guarded environment."""
    terminal = {"CRASHED", "FAILED", "REMOVED", "REMOVING", "SKIPPED"}
    running = {"BUILDING", "DEPLOYING", "INITIALIZING", "NEEDS_APPROVAL", "QUEUED",
               "SLEEPING", "SUCCESS", "WAITING"}
    try:
        wanted = {item["id"] for item in manifest["services"].values()}
        seen = set()
        result = []
        for instance in instances:
            service_id = instance["serviceId"]
            if (instance["environmentId"] != manifest["environmentId"]
                    or service_id not in wanted or service_id in seen):
                raise ValueError("Foreign or duplicate staging instance.")
            seen.add(service_id)
            deployments = list(instance["activeDeployments"])
            if instance.get("latestDeployment"):
                deployments.append(instance["latestDeployment"])
            for deployment in deployments:
                status, identity = deployment["status"], deployment["id"]
                if not isinstance(identity, str) or not identity or status not in terminal | running:
                    raise ValueError("Unexpected deployment identity/status.")
                if status in running and identity not in result:
                    result.append(identity)
        if seen != wanted:
            raise ValueError("Incomplete staging instance snapshot.")
        return result
    except (KeyError, TypeError, AttributeError) as error:
        raise ValueError("Incomplete staging deployment response.") from error
