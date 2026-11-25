WITH markers AS (
SELECT s.name,s.ts,p.pid FROM slice s JOIN thread_track tt ON s.track_id=tt.id
JOIN thread t USING(utid) JOIN process p USING(upid) WHERE s.name GLOB 'T55-*-trace-check-*'
), tokens AS (
SELECT pid, substr(name,1,instr(name,'-vsync-')-1) route_key,
CAST(substr(name,instr(name,'-vsync-')+7) AS INT) token FROM markers WHERE name LIKE '%-vsync-%'
)
SELECT tokens.route_key,tokens.pid,token,a.layer_name,a.present_type,a.display_frame_token,
d.present_type AS display_status, d.ts+d.dur AS present_ns,
ROUND((d.ts+d.dur-m.ts)/1e6,3) AS action_to_present_ms
FROM tokens JOIN markers m ON m.pid=tokens.pid AND m.name=route_key||'-action'
LEFT JOIN (actual_frame_timeline_slice a JOIN process p ON p.upid=a.upid)
ON p.pid=tokens.pid AND a.surface_frame_token=token AND a.layer_name GLOB '*VRI-lk.finora.app.acceptance*'
LEFT JOIN actual_frame_timeline_slice d ON d.display_frame_token=a.display_frame_token
AND d.surface_frame_token IS NULL AND d.layer_name IS NULL ORDER BY route_key,present_ns;
