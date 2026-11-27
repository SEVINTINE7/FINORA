WITH marks AS MATERIALIZED (
  SELECT s.name, s.ts, p.pid
  FROM slice s
  JOIN thread_track tt ON s.track_id=tt.id
  JOIN thread t USING(utid)
  JOIN process p USING(upid)
  WHERE s.name GLOB 'T55-*'
), tokens AS MATERIALIZED (
  SELECT pid, substr(name, 1, instr(name, '-vsync-') - 1) AS sample,
         CAST(substr(name, instr(name, '-vsync-') + 7) AS INT) AS token,
         CASE
           WHEN name GLOB 'T55-HOME-*' THEN '.MainActivity'
           WHEN name GLOB 'T55-RECORDS-*' THEN '.TransactionActivity'
           WHEN name GLOB 'T55-PLAN-*' THEN '.BudgetListActivity'
           WHEN name GLOB 'T55-INSIGHTS-*' THEN '.InsightsActivity'
         END AS activity
  FROM marks WHERE name LIKE '%-vsync-%'
)
SELECT k.sample, k.pid, k.token, a.layer_name, a.present_type AS surface_status,
       a.display_frame_token, d.present_type AS display_status,
       action.ts AS action_ns, ready.ts AS ready_ns, submitted.ts AS submitted_ns,
       d.ts+d.dur AS display_end_ns,
       CASE WHEN a.present_type IN ('On-time Present', 'Late Present', 'Early Present')
                  AND d.present_type IN ('On-time Present', 'Late Present', 'Early Present')
                  AND d.dur>0 AND d.ts+d.dur >= ready.ts
            THEN ROUND((d.ts+d.dur-action.ts)/1e6, 3) END AS qualified_ms
FROM tokens k
JOIN marks action ON action.pid=k.pid AND action.name=k.sample||'-action'
JOIN marks ready ON ready.pid=k.pid AND ready.name=k.sample||'-ready'
JOIN marks submitted ON submitted.pid=k.pid AND submitted.name=k.sample||'-submitted'
LEFT JOIN (actual_frame_timeline_slice a JOIN process p ON p.upid=a.upid)
  ON p.pid=k.pid AND a.surface_frame_token=k.token
  AND a.layer_name GLOB 'TX - *lk.finora.app.t55handset/*'||k.activity||'#*'
LEFT JOIN actual_frame_timeline_slice d
  ON d.display_frame_token=a.display_frame_token AND d.surface_frame_token IS NULL AND d.layer_name IS NULL
ORDER BY action.ts, display_end_ns;
