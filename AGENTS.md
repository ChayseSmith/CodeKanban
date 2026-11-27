# Working agreement

For feature changes and fixes requested by the user, complete the implementation, run checks appropriate to the change, then commit and push the completed work automatically. This is standing user authorization; no additional confirmation is required for routine commits and pushes within this scope.

- Record the checked-out branch at the start of the task. Commit and push only that branch, and verify it is still checked out before committing and pushing. If the branch changes during the task or HEAD is detached, stop publication and explain why.
- Never switch branches, merge into another branch, or push another branch without an explicit request.
- Stage only files belonging to the requested work. Preserve unrelated or pre-existing user changes; do not include them without authorization.
- Use a descriptive commit message. Push normally to the branch's configured upstream; never force-push. If no upstream exists, publish the same branch name to origin and set its upstream.
- If a normal push is rejected, preserve the commit and explain the issue. Do not rewrite history or resolve remote divergence automatically without inspecting it and obtaining any necessary authorization.
- Read-only questions do not require commits or pushes. Do not create empty commits merely to trigger CI.
- Report the commit, branch, push result, and checks performed. Distinguish a successful push from a successful CI build or release; do not claim CI completed without verifying it.

The GitHub Android release workflow publishes beta builds from non-default-branch pushes. Stable releases from the default branch are scheduled weekly on Sunday mornings. Committing and pushing does not bypass that stable schedule.
