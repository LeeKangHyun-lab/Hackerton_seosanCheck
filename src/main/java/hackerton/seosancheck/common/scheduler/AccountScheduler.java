package hackerton.seosancheck.common.scheduler;

import hackerton.seosancheck.mapper.account.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountScheduler {

    private final MemberMapper memberMapper;

    @Scheduled(cron = "*/30 * * * * *") // 매 30초마다 실행
    public void deleteOldAccounts() {
        int deleted = memberMapper.deleteOldAccounts();
        if (deleted > 0) {
            System.out.println("[Scheduler] " + deleted + "개의 오래된 계정을 삭제했습니다.");
        }
    }
}
