package com.sdl.common.config;

import com.sdl.common.config.entity.Schedule;
import com.sdl.common.config.mapper.ScheduleMapper;
import com.sdl.common.utils.DateUtil;
import com.sdl.common.utils.SpringUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;

/**
 * @program flowerPaaS
 * @description: 定时任务启动类
 * @author: songdeling
 * @create: 2019/12/24 11:53
 */
@Component
@Slf4j
@EnableScheduling
public class ScheduleConfig implements SchedulingConfigurer {

    @Value("${spring.application.name}")
    private String applicationName;

    @Resource
    private ScheduleMapper scheduleMapper;

    /**
     * 定时任务执行数
     */
    private int scheduleTaskCount = 0;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        final List<Schedule> scheduleList = scheduleMapper.getScheduleListByAppName(applicationName);

        if (null != scheduleList && !scheduleList.isEmpty()) {
            log.info("定时任务即将启动，预计启动任务数量[" + scheduleList.size() + "]，时间：" + DateUtil.getCurrentDateTime());
            for (Schedule schedule : scheduleList) {
                // 判断任务是否有效
                if (schedule.getValid()) {
                    // 执行定时任务
                    taskRegistrar.addTriggerTask(getRunnable(schedule), getTrigger(schedule));
                    scheduleTaskCount++;
                }
            }
            log.info("定时任务实际启动数量[" + scheduleTaskCount + "]，时间：" + DateUtil.getCurrentDateTime());
        }

    }

    private Runnable getRunnable(Schedule schedule) {
        return () -> {
            try {
                final Object bean = SpringUtil.getBean(schedule.getClassName());
                final Method method = bean.getClass().getMethod(schedule.getMethod(), (Class<?>[]) null);
                method.invoke(bean);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                log.error("定时任务调度失败", e);
            }
        };
    }

    private Trigger getTrigger(Schedule schedule) {
        return triggerContext -> {
            // 将Cron 0/1 * * * * ? 输入取得下一次执行的时间
            final CronTrigger cronTrigger = new CronTrigger(schedule.getCron());
            final Date date = cronTrigger.nextExecutionTime(triggerContext);
            return date;
        };
    }

}

