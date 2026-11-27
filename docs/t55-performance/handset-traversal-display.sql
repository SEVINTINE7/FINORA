-- On Android 14, the ready marker's enclosing traversal names the frame HWUI draws.
-- A Choreographer callback can advertise a different token after resync.
WITH RECURSIVE ready AS (
  SELECT s.id, s.parent_id, s.ts, p.upid, substr(s.name, 1, length(s.name)-6) AS sample
  FROM slice s JOIN thread_track tt ON s.track_id=tt.id
  JOIN thread t USING(utid) JOIN process p USING(upid)
  WHERE s.name GLOB 'T55-*-ready'
), ancestors(ready_id, parent_id, name, depth) AS (
  SELECT id,parent_id,NULL,0 FROM ready
  UNION ALL
  SELECT a.ready_id,s.parent_id,s.name,a.depth+1
  FROM ancestors a JOIN slice s ON s.id=a.parent_id
), traversal AS (
  SELECT ready_id, CAST(substr(name, length('Choreographer#doFrame ')+1) AS INT) AS token
  FROM ancestors
  WHERE name GLOB 'Choreographer#doFrame [0-9]*'
    AND substr(name, length('Choreographer#doFrame ')+1) NOT GLOB '*[^0-9]*'
), marks AS (
  SELECT s.name,s.ts,p.upid FROM slice s JOIN thread_track tt ON s.track_id=tt.id
  JOIN thread t USING(utid) JOIN process p USING(upid)
  WHERE s.name GLOB 'T55-*-action' OR s.name GLOB 'T55-*-submitted'
), matches AS (
  SELECT r.sample,k.token,a.layer_name,a.present_type AS surface_status,
         d.present_type AS display_status,d.display_frame_token,
         action.ts AS action_ns,r.ts AS ready_ns,submitted.ts AS submitted_ns,
         d.ts+d.dur AS display_end_ns
  FROM ready r LEFT JOIN traversal k ON k.ready_id=r.id
  LEFT JOIN marks action ON action.upid=r.upid AND action.name=r.sample||'-action'
  LEFT JOIN marks submitted ON submitted.upid=r.upid AND submitted.name=r.sample||'-submitted'
  LEFT JOIN actual_frame_timeline_slice a ON a.upid=r.upid AND a.surface_frame_token=k.token
    AND a.layer_name GLOB 'TX - *lk.finora.app.t55handset/*'||
      CASE
        WHEN r.sample GLOB 'T55-HOME-*' THEN '.MainActivity'
        WHEN r.sample GLOB 'T55-RECORDS-*' THEN '.TransactionActivity'
        WHEN r.sample GLOB 'T55-PLAN-*' THEN '.BudgetListActivity'
        WHEN r.sample GLOB 'T55-INSIGHTS-*' THEN '.InsightsActivity'
      END||'#[0-9]*'
  LEFT JOIN actual_frame_timeline_slice d ON d.display_frame_token=a.display_frame_token
    AND d.surface_frame_token IS NULL AND d.layer_name IS NULL AND d.dur>0
)
SELECT *, CASE
    WHEN count(*) OVER (PARTITION BY sample)=1
      AND surface_status IN ('On-time Present','Late Present','Early Present')
      AND display_status IN ('On-time Present','Late Present','Early Present')
      AND action_ns<=ready_ns AND ready_ns<=submitted_ns AND ready_ns<=display_end_ns
    THEN round((display_end_ns-action_ns)/1e6,3)
  END AS qualified_ms
FROM matches ORDER BY action_ns,display_end_ns;
