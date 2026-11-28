-- Separate the first app presentation from a Home frame whose pre-draw
-- contained both visible canonical balances. All timestamps are trace clock.
WITH RECURSIVE starts AS (
  SELECT s.ts AS request_ns,
         lead(s.ts, 1, 9223372036854775807) OVER (ORDER BY s.ts) AS next_request_ns,
         row_number() OVER (ORDER BY s.ts) AS attempt
  FROM slice s JOIN thread_track tt ON s.track_id=tt.id
  JOIN thread t USING(utid) JOIN process p USING(upid)
  WHERE p.name = 'system_server'
    AND s.name = 'Start proc: lk.finora.app.t57handset' AND s.dur > 0
), ancestors(upid, parent_id, name, ready_ns) AS (
  SELECT t.upid, s.parent_id, NULL, s.ts
  FROM slice s JOIN thread_track tt ON s.track_id=tt.id JOIN thread t USING(utid)
  WHERE s.name='T55-external-home-usable'
  UNION ALL
  SELECT a.upid, s.parent_id, s.name, a.ready_ns
  FROM ancestors a JOIN slice s ON s.id=a.parent_id
), ready AS (
  SELECT upid, ready_ns, CAST(substr(name,length('Choreographer#doFrame ')+1) AS INT) AS token
  FROM ancestors
  WHERE name GLOB 'Choreographer#doFrame [0-9]*'
    AND substr(name,length('Choreographer#doFrame ')+1) NOT GLOB '*[^0-9]*'
), presented AS (
  SELECT a.upid, a.ts AS surface_ns, a.surface_frame_token AS token, a.layer_name,
         d.ts + d.dur AS displayed_ns
  FROM actual_frame_timeline_slice a JOIN actual_frame_timeline_slice d USING(display_frame_token)
  WHERE (a.layer_name GLOB 'TX - lk.finora.app.t57handset/*Activity#[0-9]*'
      OR a.layer_name GLOB 'TX - VRI-lk.finora.app.t57handset/*Activity#[0-9]*')
    AND a.present_type IN ('On-time Present','Late Present','Early Present')
    AND d.surface_frame_token IS NULL AND d.layer_name IS NULL AND d.dur>0
    AND d.present_type IN ('On-time Present','Late Present','Early Present')
), cohort AS (
  SELECT s.attempt, s.request_ns, min(p.displayed_ns) AS first_presentation_ns
  FROM starts s LEFT JOIN presented p
    ON p.surface_ns>=s.request_ns AND p.displayed_ns<s.next_request_ns
  GROUP BY s.attempt, s.request_ns
), usable AS (
  SELECT s.attempt, CASE WHEN count(*)=1 THEN min(p.displayed_ns) END AS usable_home_ns
  FROM starts s JOIN ready r ON r.ready_ns>=s.request_ns AND r.ready_ns<s.next_request_ns
  JOIN presented p ON p.upid=r.upid AND p.token=r.token
    AND p.layer_name GLOB '*MainActivity#[0-9]*'
    AND p.displayed_ns>=r.ready_ns AND p.displayed_ns<s.next_request_ns
  GROUP BY s.attempt
)
SELECT c.attempt, c.request_ns, c.first_presentation_ns, u.usable_home_ns,
       round((c.first_presentation_ns-c.request_ns)/1e6,3) AS first_presentation_ms,
       round((u.usable_home_ns-c.request_ns)/1e6,3) AS usable_home_ms
FROM cohort c LEFT JOIN usable u USING(attempt) ORDER BY c.attempt;
