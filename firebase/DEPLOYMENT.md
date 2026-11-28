# Automatic Firestore rules deployment

This uses a Google service account with **Firebase Rules Admin** and GitHub Workload Identity Federation. No service-account private key, Firebase login token, Cloud Functions service or paid server is required. IAM API usage is free; existing Firestore/GitHub usage limits still apply.

The Android release workflow tests the rules in a demo emulator, builds/signs the app, then deploys the rules **before publishing the APK**. A deployment failure blocks publication. Until setup is complete, the feature is disabled and existing APK releases continue normally. Unchanged rules are skipped to avoid creating unnecessary rulesets. The default Firestore database is the target.

## One-time Google setup

1. Open https://console.cloud.google.com/ using the same Google account that owns the Firebase project.
2. In the project selector at the top, choose **Kanban** (`kanban-83cd1`). Google Cloud and Firebase are two management views of the same project.
3. Click **Activate Cloud Shell** (the terminal icon near the top-right). This opens a terminal in your browser; authorize it when requested. No local software installation or service-account key is needed.
4. In that terminal, clone the public repository branch containing this setup:

```bash
git clone --depth 1 --branch feature/initial-kanban https://github.com/ChayseSmith/CodeKanban.git kanban-deploy-setup
cd kanban-deploy-setup
```

After the feature is merged into main, the `--branch feature/initial-kanban` option can be omitted.

5. Open/read `firebase/setup-github-deployment.sh`, then run:

```bash
bash firebase/setup-github-deployment.sh
```

The script enables the IAM, service-account credentials, STS and Firebase Rules APIs; creates `github-firestore-rules@kanban-83cd1.iam.gserviceaccount.com`; grants **Firebase Rules Admin**; creates a GitHub identity pool/provider; and allows this exact repository to impersonate that account. It may take a few minutes for IAM changes to propagate. Re-running it updates/reuses the same named resources.

Trust is restricted to repository ID `1473624000`, owner ID `147212510`, the Android release workflow path, and push/schedule/manual events on branches. Fork PR workflows are not trusted. All branch pushes within this repository that run the release workflow are trusted for rules deployment, matching the beta-release policy. Treat permission to push workflow/rules changes as production deployment access.

## One-time GitHub setup

In the GitHub repository, open **Settings → Secrets and variables → Actions → Variables**. Choose **New repository variable** for each value printed by the setup script:

| Variable | Value |
| --- | --- |
| `FIREBASE_WORKLOAD_IDENTITY_PROVIDER` | The complete `projects/.../locations/global/workloadIdentityPools/.../providers/...` value printed by the script |
| `FIREBASE_DEPLOY_SERVICE_ACCOUNT` | `github-firestore-rules@kanban-83cd1.iam.gserviceaccount.com` |
| `FIREBASE_RULES_DEPLOY_ENABLED` | `true` |

These are non-secret resource identifiers, so use **Variables**, not Secrets. No JSON credential file needs to be created, downloaded or committed. Add the first two variables before enabling the third.

Run the Android release workflow manually on the feature branch or push the next change. The first enabled deployment may need to wait a few minutes for Google IAM propagation. Inspect **Authenticate Firebase rules deployer** and **Deploy Firestore rules before publishing APK** in the Actions log. Missing/incorrect configuration or API permissions will fail deployment and block APK publication, rather than silently skip it.

## What happens afterwards

- Successful beta releases can deploy updated rules immediately; stable releases remain scheduled for Sunday mornings.
- Beta and Stable currently use the **same Firebase project/database**. Rules affect both channels and older installed APKs. Keep rule changes backwards compatible, or introduce a separate beta Firebase project before testing incompatible changes.
- The repository's `firebase/firestore.rules` becomes the source of truth. Console edits will be overwritten by the next enabled deployment if they differ from the committed rules.
- Tests run without production credentials against `demo-kanban-sync`; authentication happens only after tests and the APK build finish, immediately before deployment.
- Only the Rules API is used for deployment. The service account has no Firestore data-editor role and no project Owner/Editor grant. A Rules Admin can change access policy, so it is still a privileged deployment identity.
- Set `FIREBASE_RULES_DEPLOY_ENABLED=false` to stop automatic rules publication while allowing normal app releases. Manual publication through Firebase remains available.
- Live authentication/deployment must be verified after you complete the Google/GitHub setup; local tests cannot impersonate your service account.

References: [Google GitHub authentication](https://github.com/google-github-actions/auth), [Firebase Rules API](https://firebase.google.com/docs/reference/rules/rest), [Rules Admin permissions](https://docs.cloud.google.com/iam/docs/roles-permissions/firebaserules), [IAM pricing](https://cloud.google.com/iam/pricing).
