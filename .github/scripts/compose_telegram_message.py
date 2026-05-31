import html
import os

FAILURE_STATES = {"failure", "cancelled", "timed_out", "action_required"}
SUCCESS_STATES = {"success", "skipped", "neutral"}

JOB_RESULT_FIELDS = (
    ("backend", "BACKEND_RESULT"),
    ("sonar-backend", "SONAR_BACKEND_RESULT"),
)

SONAR_SKIP_FLAGS = {
    "SONAR_BACKEND_RESULT": "SONAR_BACKEND_SKIPPED",
}


def normalize(value: str) -> str:
    return (value or "unknown").lower()


def status_icon(status: str) -> str:
    if status == "success":
        return "✅"
    if status in FAILURE_STATES:
        return "❌"
    if status in {"skipped", "neutral"}:
        return "⏭️"
    return "❓"


def get_actual_status(env_name: str, result: str) -> str:
    skip_flag = SONAR_SKIP_FLAGS.get(env_name)
    if skip_flag and normalize(os.environ.get(skip_flag, "")) == "true":
        return "skipped"
    return result


def overall_status() -> str:
    statuses = []
    for _, env_name in JOB_RESULT_FIELDS:
        result = normalize(os.environ.get(env_name, "unknown"))
        actual = get_actual_status(env_name, result)
        statuses.append(actual)

    if any(status in FAILURE_STATES for status in statuses):
        return "failure"
    if all(status in SUCCESS_STATES for status in statuses):
        return "success"
    return "unknown"


def esc(value: str) -> str:
    return html.escape(str(value), quote=True)


def branch_name() -> str:
    return os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_REF_NAME") or ""


def repo_name() -> str:
    full_name = os.environ.get("GITHUB_REPOSITORY", "")
    if "/" in full_name:
        return full_name.split("/", 1)[1]
    return full_name


def compose_message() -> str:
    current_overall = overall_status()
    lines = [
        f"{esc(repo_name() or 'repository')} CI finished",
        "",
        f"{status_icon(current_overall)} <b>Status:</b> {esc(current_overall)}",
        f"<b>Branch:</b> {esc(branch_name())}",
        f"<b>Commit:</b> {esc(os.environ.get('GITHUB_SHA', ''))}",
        f"<b>Actor:</b> {esc(os.environ.get('GITHUB_ACTOR', ''))}",
        f"<b>Workflow:</b> {esc(os.environ.get('GITHUB_WORKFLOW', ''))}",
        "",
        "<b>Job results</b>",
    ]

    for job_name, env_name in JOB_RESULT_FIELDS:
        result = normalize(os.environ.get(env_name, "unknown"))
        actual = get_actual_status(env_name, result)
        lines.append(f"- {status_icon(actual)} {esc(job_name)}: {esc(actual)}")

    lines.append("")
    lines.append(
        f"Link: {esc(os.environ.get('GITHUB_SERVER_URL', ''))}/{esc(os.environ.get('GITHUB_REPOSITORY', ''))}/actions/runs/{esc(os.environ.get('GITHUB_RUN_ID', ''))}"
    )

    return "\n".join(lines)


def main() -> None:
    message = compose_message()
    output_path = os.environ["GITHUB_OUTPUT"]
    with open(output_path, "a", encoding="utf-8") as output_file:
        output_file.write("message<<EOF\n")
        output_file.write(message)
        output_file.write("\nEOF\n")


if __name__ == "__main__":
    main()
