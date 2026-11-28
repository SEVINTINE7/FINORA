-- External launches use the system-server process-start request and the first
-- actual MainActivity presentation. This boundary can include Home placeholders.
WITH starts AS (
  SELECT s.ts AS request_ns,
         lead(s.ts, 1, 9223372036854775807) OVER (ORDER BY s.ts) AS next_request_ns,
         row_number() OVER (ORDER BY s.ts) AS attempt
  FROM slice s JOIN thread_track tt ON s.track_id=tt.id
  JOIN thread t USING(utid) JOIN process p USING(upid)
  WHERE p.name = 'system_server'
    AND s.name = 'Start proc: lk.finora.app.t57handset' AND s.dur > 0
), presentations AS (
  SELECT a.ts AS surface_ns, d.ts + d.dur AS displayed_ns
  FROM actual_frame_timeline_slice a JOIN actual_frame_timeline_slice d USING(display_frame_token)
  JOIN process p ON a.upid = p.upid
  WHERE p.name = 'lk.finora.app.t57handset'
    AND a.layer_name GLOB 'TX - *lk.finora.app.t57handset/*MainActivity#[0-9]*'
    AND a.present_type IN ('On-time Present','Late Present','Early Present')
    AND d.surface_frame_token IS NULL AND d.layer_name IS NULL AND d.dur > 0
    AND d.present_type IN ('On-time Present','Late Present','Early Present')
)
SELECT s.attempt, s.request_ns, min(p.displayed_ns) AS first_home_presentation_ns,
       round((min(p.displayed_ns)-s.request_ns)/1e6, 3) AS request_to_first_home_ms
FROM starts s LEFT JOIN presentations p
  ON p.surface_ns >= s.request_ns AND p.displayed_ns < s.next_request_ns
GROUP BY s.attempt, s.request_ns
ORDER BY s.attempt;
