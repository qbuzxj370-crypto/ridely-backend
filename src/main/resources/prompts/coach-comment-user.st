아래 코스가 확정됐습니다. 라이더에게 건넬 코칭을 써 주세요.

[확정된 코스]
- 형태: <routeShape>
- 실제 주행 거리: <totalDistanceKm> km
- 예상 소요 시간: <durationMin> 분
- 운동 강도: <intensityLevel>
- 목표 거리: <targetDistanceKm> km

[설계 의도]
<designIntent>

[경유지]
<if(waypoints)>
<waypoints:{w|- <w.progressKm>km 지점 | <w.label> | <w.reason>
}>
<else>
(경유지 없음)
<endif>

[작성 규칙]
- title에는 지명이나 대표 경유지 이름을 넣습니다. 예: "한강 따라 14km, 적당히 땀 빼는 코스"
- "12km 편도 코스"처럼 어디를 가는지 알 수 없는 제목은 쓰지 않습니다.
- highlights는 2~4개. 경유지를 `Nkm 지점 OO 경유` 형태로 씁니다.
- coachComment는 1~2문장. 오늘 이 코스를 어떻게 타면 좋을지 페이스 위주로 말합니다.
- 실제 거리가 목표 거리와 1km 넘게 다르면 coachComment에서 자연스럽게 짚어 줍니다. 다만 거리가 짧다고 "한 번 더 도세요" 같은 제안은 하지 마세요. 이번 코스가 그렇게 나왔다는 사실만 담담히 전합니다.
- 위에 적힌 형태와 거리를 그대로 씁니다. 편도 코스를 순환이라고 하거나, 실제 거리 대신 목표 거리를 제목에 넣지 마세요.
- dangerZoneAlert는 비워 두세요. 이번 코스에는 사고다발지 정보가 없습니다.
- nextStepSuggestion도 비워 두세요. 지난 라이딩 기록이 없습니다.
