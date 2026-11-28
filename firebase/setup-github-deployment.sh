#!/usr/bin/env bash
# Run once as the project owner in Google Cloud Shell. No private key is created.
set -euo pipefail
PROJECT_ID="kanban-83cd1"
POOL="github-kanban"
PROVIDER="github-actions"
ACCOUNT="github-firestore-rules"
REPOSITORY_ID="1473624000"
OWNER_ID="147212510"

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
gcloud services enable iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com firebaserules.googleapis.com --project="$PROJECT_ID"
SA="$ACCOUNT@$PROJECT_ID.iam.gserviceaccount.com"
if ! gcloud iam service-accounts describe "$SA" --project="$PROJECT_ID" >/dev/null 2>&1; then
  gcloud iam service-accounts create "$ACCOUNT" --project="$PROJECT_ID" --display-name="GitHub Firestore rules deployment"
fi
gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$SA" --role="roles/firebaserules.admin" --condition=None >/dev/null
if ! gcloud iam workload-identity-pools describe "$POOL" --location=global --project="$PROJECT_ID" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create "$POOL" --location=global --project="$PROJECT_ID" --display-name="Kanban GitHub Actions"
fi
CONDITION="assertion.repository_id == '$REPOSITORY_ID' && assertion.repository_owner_id == '$OWNER_ID' && assertion.event_name in ['push','schedule','workflow_dispatch'] && assertion.workflow_ref.startsWith('ChayseSmith/CodeKanban/.github/workflows/android-release.yml@refs/heads/')"
if gcloud iam workload-identity-pools providers describe "$PROVIDER" --workload-identity-pool="$POOL" --location=global --project="$PROJECT_ID" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers update-oidc "$PROVIDER" --workload-identity-pool="$POOL" --location=global --project="$PROJECT_ID" --issuer-uri="https://token.actions.githubusercontent.com" --attribute-mapping="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id" --attribute-condition="$CONDITION"
else
  gcloud iam workload-identity-pools providers create-oidc "$PROVIDER" --workload-identity-pool="$POOL" --location=global --project="$PROJECT_ID" --issuer-uri="https://token.actions.githubusercontent.com" --attribute-mapping="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id" --attribute-condition="$CONDITION"
fi
gcloud iam service-accounts add-iam-policy-binding "$SA" --project="$PROJECT_ID" --role="roles/iam.workloadIdentityUser" --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$POOL/attribute.repository_id/$REPOSITORY_ID" >/dev/null
printf '\nAdd these GitHub Actions repository VARIABLES (not secrets):\n'
printf 'FIREBASE_WORKLOAD_IDENTITY_PROVIDER=projects/%s/locations/global/workloadIdentityPools/%s/providers/%s\n' "$PROJECT_NUMBER" "$POOL" "$PROVIDER"
printf 'FIREBASE_DEPLOY_SERVICE_ACCOUNT=%s\n' "$SA"
printf 'FIREBASE_RULES_DEPLOY_ENABLED=true\n'
