package com.somepro.interfaces.rest.hwaste;

import com.somepro.application.hwaste.TransferPlanAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hwaste.converter.TransferPlanVoConverter;
import com.somepro.interfaces.rest.hwaste.vo.PageVO;
import com.somepro.interfaces.rest.hwaste.vo.TransferPlanVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * 年度转移计划接口（用户接口层）：申报（立草稿 → 申报）、改量、批复、驳回、追加申报，
 * 以及详情与多条件翻页。
 *
 * 状态流转类接口的计划定位参数：planId 或 planNo 传其一即可。
 */
@RestController
@RequestMapping("/api/hwaste/plan")
public class TransferPlanController {

    private final TransferPlanAppService transferPlanAppService;

    public TransferPlanController(TransferPlanAppService transferPlanAppService) {
        this.transferPlanAppService = transferPlanAppService;
    }

    /** 立单落草稿：同单位同类别同年度只准挂一份；草稿不校在库，申报时再算。 */
    @PostMapping("/create")
    public Mono<Result<TransferPlanVO>> create(@RequestParam(required = false) Long sourceId,
                                               @RequestParam(required = false) String categoryCode,
                                               @RequestParam(required = false) Integer planYear,
                                               @RequestParam(required = false) BigDecimal plannedWeight) {
        return transferPlanAppService.create(sourceId, categoryCode, planYear, plannedWeight)
                .map(TransferPlanVoConverter::toVo)
                .map(Result::ok);
    }

    /** 改申报量：只有草稿 / 首轮被驳回能改。 */
    @PostMapping("/edit")
    public Mono<Result<TransferPlanVO>> edit(@RequestParam(required = false) Long planId,
                                             @RequestParam(required = false) String planNo,
                                             @RequestParam(required = false) BigDecimal plannedWeight) {
        return transferPlanAppService.edit(planId, planNo, plannedWeight)
                .map(TransferPlanVoConverter::toVo)
                .map(Result::ok);
    }

    /** 首轮申报：草稿 / 驳回 → 已申报；申报量不能盖过该单位该类别当前在库总量。 */
    @PostMapping("/submit")
    public Mono<Result<TransferPlanVO>> submit(@RequestParam(required = false) Long planId,
                                               @RequestParam(required = false) String planNo) {
        return transferPlanAppService.submit(planId, planNo)
                .map(TransferPlanVoConverter::toVo)
                .map(Result::ok);
    }

    /**
     * 追加申报：已批复计划当年再发起一次。累计申报量（原申报 + 追加）不能盖过在库总量；
     * 批下来之前批复额度不放宽；同时两笔追加只成一笔。
     */
    @PostMapping("/append")
    public Mono<Result<TransferPlanVO>> append(@RequestParam(required = false) Long planId,
                                               @RequestParam(required = false) String planNo,
                                               @RequestParam(required = false) BigDecimal appendWeight) {
        return transferPlanAppService.appendSubmit(planId, planNo, appendWeight)
                .map(TransferPlanVoConverter::toVo)
                .map(Result::ok);
    }

    /** 批复：已申报 → 已批复，approvedWeight 传批复后的额度总量，不能超过当前申报总量。 */
    @PostMapping("/approve")
    public Mono<Result<TransferPlanVO>> approve(@RequestParam(required = false) Long planId,
                                                @RequestParam(required = false) String planNo,
                                                @RequestParam(required = false) BigDecimal approvedWeight) {
        return transferPlanAppService.approve(planId, planNo, approvedWeight)
                .map(TransferPlanVoConverter::toVo)
                .map(Result::ok);
    }

    /** 驳回：必须写明理由；首轮驳回可改后再报，追加驳回退回已批复并释放当年追加资格。 */
    @PostMapping("/reject")
    public Mono<Result<TransferPlanVO>> reject(@RequestParam(required = false) Long planId,
                                               @RequestParam(required = false) String planNo,
                                               @RequestParam(required = false) String reason) {
        return transferPlanAppService.reject(planId, planNo, reason)
                .map(TransferPlanVoConverter::toVo)
                .map(Result::ok);
    }

    /** 计划详情：申报量、批复量、该单位该类别当前在库总量一并带出。 */
    @GetMapping("/detail")
    public Mono<Result<TransferPlanVO>> detail(@RequestParam(required = false) Long planId,
                                               @RequestParam(required = false) String planNo) {
        return transferPlanAppService.detail(planId, planNo)
                .map(TransferPlanVoConverter::toDetailVo)
                .map(Result::ok);
    }

    /** 分页查询：单位 / 类别 / 年度 / 状态均可选，都不传则分页列全；每行带计划编号。 */
    @GetMapping("/page")
    public Mono<Result<PageVO<TransferPlanVO>>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                     @RequestParam(required = false) Long sourceId,
                                                     @RequestParam(required = false) String categoryCode,
                                                     @RequestParam(required = false) Integer planYear,
                                                     @RequestParam(required = false) String status) {
        return transferPlanAppService.page(pageNum, pageSize, sourceId, categoryCode, planYear, status)
                .map(TransferPlanVoConverter::toPageVo)
                .map(Result::ok);
    }
}
