package kr.ridely.dto.route;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 코스 설계에 넘길 후보 묶음.
 *
 * 타입별로 나눠 담는 이유는 프롬프트에서 섹션을 나눠 보여주기 위해서다. 한 목록으로 섞으면 LLM이 관광지만 고르거나 급수대만 고르는 쏠림이 생긴다.
 *
 * 개수는 수집기가 타입별로 잘라서 채운다. 토큰 예산 때문이며 근거는 TourCandidateCollector·InfraCandidateCollector 주석에 있다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouteCandidatesDTO {

    /** 관광지·문화시설 */
    private List<CandidateDTO> tours = new ArrayList<>();

    /** 급수대 */
    private List<CandidateDTO> waters = new ArrayList<>();

    /** 자전거 수리소 */
    private List<CandidateDTO> repairShops = new ArrayList<>();

    /** 따릉이 대여소 */
    private List<CandidateDTO> bikeStations = new ArrayList<>();

    /** 전체를 한 목록으로 편다. 프롬프트 렌더링과 실재 판정에 쓴다 */
    public List<CandidateDTO> all() {
        List<CandidateDTO> merged = new ArrayList<>();
        merged.addAll(tours);
        merged.addAll(waters);
        merged.addAll(repairShops);
        merged.addAll(bikeStations);
        return merged;
    }

    public int totalCount() {
        return tours.size() + waters.size() + repairShops.size() + bikeStations.size();
    }

    /**
     * LLM이 돌려준 (type, id)에 해당하는 후보를 찾는다.
     *
     * 없으면 비어 있는 값이 온다. LLM이 실재하지 않는 ID를 만들어내는 경우가 있어, 경유지를 확정하기 전에 이걸로 걸러야 한다. 지금은 걸러내기만 하고 재호출은 하지 않는다.
     */
    public Optional<CandidateDTO> find(String type, Long id) {
        return all().stream().filter(c -> c.matches(type, id)).findFirst();
    }

    /** 후보가 하나도 없으면 코스를 설계할 수 없다 */
    public boolean isEmpty() {
        return totalCount() == 0;
    }
}
