-- First materialize handset-redesign-display.sql as the Perfetto table home_presentations.
-- The boundary is the system_server process-start operation. The previous display
-- and next action delimit one unique start per forced-stop, instrumentation-mediated launch.
WITH home AS (
  SELECT sample, action_ns, display_end_ns,
         lag(display_end_ns, 1, 0) OVER (ORDER BY action_ns) AS preceding_display_ns
  FROM home_presentations
  WHERE sample GLOB 'T55-HOME-fresh-process-launch-*'
    AND qualified_ms IS NOT NULL
), starts AS (
  SELECT s.ts, s.dur
  FROM slice s JOIN thread_track tt ON s.track_id=tt.id
  JOIN thread t USING (utid) JOIN process p USING (upid)
  WHERE p.name = 'system_server'
    AND s.name = 'Start proc: lk.finora.app.t57handset' AND s.dur > 0
), joined AS (
  SELECT h.sample, h.action_ns, h.display_end_ns, s.ts AS process_request_ns,
         count(s.ts) OVER (PARTITION BY h.sample) AS candidates
  FROM home h LEFT JOIN starts s
    ON s.ts > h.preceding_display_ns AND s.ts+s.dur <= h.action_ns
)
SELECT sample, candidates, process_request_ns, action_ns, display_end_ns,
       CASE WHEN candidates = 1 THEN round((display_end_ns-process_request_ns)/1e6, 3)
       END AS request_to_display_ms
FROM joined
ORDER BY action_ns;
