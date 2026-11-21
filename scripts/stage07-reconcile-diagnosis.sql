-- Run for one reviewed V6 owner after quiescing the old diagnosis process.
-- Set psql variables organization_id and incident_id, using the normal restricted role.
\set ON_ERROR_STOP on
BEGIN;
SELECT set_config('devinsight.organization_id', CAST(CAST(:'organization_id' AS uuid) AS text), true);

INSERT INTO public.diagnosis_jobs(id,organization_id,incident_id,log_id,max_attempts)
SELECT gen_random_uuid(),i.organization_id,i.id,l.id,4
FROM public.incidents i
JOIN public.logs l ON l.organization_id=i.organization_id AND l.id=i.diagnosis_log_id
WHERE i.organization_id=CAST(:'organization_id' AS uuid)
  AND i.id=CAST(:'incident_id' AS uuid) AND i.rule_version=1
  AND l.llm_analysis_status IN ('PENDING','PROCESSING')
  AND l.llm_summary IS NULL AND l.llm_root_cause IS NULL
  AND l.llm_recommendation IS NULL AND l.llm_analyzed_at IS NULL
ON CONFLICT DO NOTHING
RETURNING id;
COMMIT;
