아래 조건으로 자전거 코스를 설계해 주세요.

[주행 조건]
- 목표 거리: <targetDistanceKm> km
- 형태: <routeShape>
- 우선순위: 편의 <priorityConvenience> / 운동 <priorityExercise> / 풍경 <priorityScenery>

[거리 설계]
<if(circular)>
출발지로 되돌아오는 코스라 경유지를 어떻게 배치하느냐로 거리가 정해집니다.
목표 <targetDistanceKm>km를 채우려면 출발지에서 충분히 멀리 나갔다 돌아오는 배치가 필요합니다.
<else>
출발지에서 도착지까지 직선거리는 약 <straightLineKm>km입니다.
목표가 <targetDistanceKm>km이므로 경유지로 약 <slackKm>km(<detourBudgetM>m)를 더 채워야 합니다.
<endif>

경유지를 고르면 그만큼 경로가 길어집니다. 아래 목록의 `우회 +Nm`이 그 지점을 들렀다 나오는 데 드는 추가 거리입니다.
고른 경유지들의 `우회 +Nm`을 모두 더한 값이 <detourBudgetM>m에 가까워지도록 조합하세요. 축에 붙은 지점만 고르면 합이 거의 0이 되어 목표 거리에 크게 못 미칩니다.
다만 거리를 채우려고 코스와 상관없는 지점을 넣지는 마세요.

[경유지 후보]
아래 목록에 있는 것만 고를 수 있습니다. 목록에 없는 번호를 만들어내지 마세요.
`Nkm 지점`은 출발지에서 경로를 따라 어느 정도 왔을 때 지나는지를 뜻합니다.

<if(tours)>
[관광지]
<tours:{c|- type=<c.type> id=<c.id> | <c.label> | <c.progressKm>km 지점 | 경로에서 <c.offRouteM>m | 우회 +<c.detourM>m
}>
<endif>
<if(waters)>
[급수대]
<waters:{c|- type=<c.type> id=<c.id> | <c.label> | <c.progressKm>km 지점 | 경로에서 <c.offRouteM>m | 우회 +<c.detourM>m
}>
<endif>
<if(repairShops)>
[수리소]
<repairShops:{c|- type=<c.type> id=<c.id> | <c.label> | <c.progressKm>km 지점 | 경로에서 <c.offRouteM>m | 우회 +<c.detourM>m
}>
<endif>
<if(bikeStations)>
[따릉이 대여소]
<bikeStations:{c|- type=<c.type> id=<c.id> | <c.label> | <c.progressKm>km 지점 | 경로에서 <c.offRouteM>m | 우회 +<c.detourM>m
}>
<endif>

[출력 규칙 — 반드시 지킵니다]
- `type`은 목록에 적힌 `type=` 값을 영문 그대로 씁니다. TOUR / WATER / REPAIR_SHOP / BIKE_STATION 중 하나입니다.
- 한국어 이름(관광지·급수대·수리소·따릉이 대여소)을 `type`에 쓰지 마세요. 그 값은 사람이 읽는 분류일 뿐입니다.
- `id`도 목록에 적힌 숫자를 그대로 씁니다.

[설계 규칙]
- 경유지는 2~5곳. 목표 거리가 짧으면 적게, 길면 많게 잡습니다.
- 배열 순서가 주행 순서입니다. `Nkm 지점` 값이 커지는 순서로 배치하세요.
- 우선순위 가중치가 높은 종류를 더 많이 고릅니다. 편의는 급수대·수리소·따릉이, 풍경은 관광지입니다.
- 같은 지점을 두 번 넣지 마세요.
- reason은 한 문장으로, 왜 그 지점을 그 타이밍에 넣었는지 씁니다.
- designIntent는 코스 전체를 어떤 의도로 짰는지 한 문장으로 씁니다.

[종류 균형 — 한 종류로 쏠리지 않게 합니다]
- 풍경 가중치가 0이 아니면 관광지를 최소 한 곳 넣습니다. 코스에 볼거리가 없으면 라이딩이 단조로워집니다.
- 급수대는 최대 두 곳. 물 마실 곳은 코스 전반과 후반에 하나씩이면 충분합니다.
- 한 종류가 전체 경유지의 절반을 넘지 않게 합니다.
