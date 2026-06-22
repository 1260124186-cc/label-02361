package com.factory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManufacturingTask 单元测试")
class ManufacturingTaskTest {

    @Test
    @DisplayName("构造函数：参数无效应抛出 IllegalArgumentException")
    void testConstructorInvalid() {
        assertThrows(IllegalArgumentException.class, () -> new ManufacturingTask(0, 1, 2, 100));
        assertThrows(IllegalArgumentException.class, () -> new ManufacturingTask(1, 0, 2, 100));
        assertThrows(IllegalArgumentException.class, () -> new ManufacturingTask(1, 1, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new ManufacturingTask(1, 1, 2, -1));
    }

    @Test
    @DisplayName("构造函数：有效参数应正常创建，状态为 PENDING")
    void testConstructorValid() {
        ManufacturingTask task = new ManufacturingTask(1, 2, 3, 1000);
        assertEquals(1, task.getTaskId());
        assertEquals(2, task.getPID());
        assertEquals(3, task.getRequiredPeople());
        assertEquals(1000, task.getManufacturingDurationMs());
        assertEquals(ManufacturingTask.Status.PENDING, task.getStatus());
        assertNotNull(task.getCreatedAt());
        assertNull(task.getStartTime());
        assertNull(task.getEndTime());
        assertTrue(task.getAssignedMembers().isEmpty());
        assertEquals(0, task.getCompletedSubtasks());
        assertEquals(0, task.getFailedSubtasks());
    }

    @Test
    @DisplayName("assignMember：PENDING 状态且成员技能匹配时应成功分配")
    void testAssignMemberSuccess() {
        ManufacturingTask task = new ManufacturingTask(1, 1, 2, 100);
        TeamMember member1 = new TeamMember(1, "Worker-1");
        TeamMember member2 = new TeamMember(2, "Worker-2");

        task.assignMember(member1);
        assertEquals(1, task.getAssignedMembers().size());
        assertTrue(member1.isAvailable() == false);

        task.assignMember(member2);
        assertEquals(2, task.getAssignedMembers().size());
    }

    @Test
    @DisplayName("assignMember：成员不具备技能时应抛出 IllegalArgumentException")
    void testAssignMemberUnskilled() {
        ManufacturingTask task = new ManufacturingTask(1, 1, 1, 100);
        TeamMember member = new TeamMember(1, "Worker-1", Set.of(2, 3));

        assertThrows(IllegalArgumentException.class, () -> task.assignMember(member));
    }

    @Test
    @DisplayName("assignMember：任务已开始后应抛出 IllegalStateException")
    void testAssignMemberAfterStart() {
        ManufacturingTask task = new ManufacturingTask(1, 1, 1, 100);
        TeamMember member = new TeamMember(1, "Worker-1");
        task.assignMember(member);
        task.start();

        TeamMember member2 = new TeamMember(2, "Worker-2");
        assertThrows(IllegalStateException.class, () -> task.assignMember(member2));
    }

    @Test
    @DisplayName("assignMember：人数已满时应抛出 IllegalStateException")
    void testAssignMemberFull() {
        ManufacturingTask task = new ManufacturingTask(1, 1, 1, 100);
        TeamMember member1 = new TeamMember(1, "Worker-1");
        TeamMember member2 = new TeamMember(2, "Worker-2");

        task.assignMember(member1);
        assertThrows(IllegalStateException.class, () -> task.assignMember(member2));
    }

    @Test
    @DisplayName("start：PENDING 状态且人数足够时应成功启动")
    void testStartSuccess() {
        ManufacturingTask task = new ManufacturingTask(1, 1, 1, 100);
        TeamMember member = new TeamMember(1, "Worker-1");
        task.assignMember(member);

        task.start();
        assertEquals(ManufacturingTask.Status.IN_PROGRESS, task.getStatus());
        assertNotNull(task.getStartTime());
    }

    @Test
    @DisplayName("start：人数不足时应抛出 IllegalStateException")
    void testStartInsufficientMembers() {
        ManufacturingTask task = new ManufacturingTask(1, 1, 2, 100);
        TeamMember member = new TeamMember(1, "Worker-1");
        task.assignMember(member);

        assertThrows(IllegalStateException.class, () -> task.start());
    }

    @Test
    @DisplayName("start：非 PENDING 状态时应抛出 IllegalStateException")
    void testStartNotPending() {
        ManufacturingTask task = new ManufacturingTask(1, 1, 1, 100);
        TeamMember member = new TeamMember(1, "Worker-1");
        task.assignMember(member);
        task.start();

        assertThrows(IllegalStateException.class, () -> task.start());
    }

    @Test
    @DisplayName("markSubtaskComplete：所有子任务完成后状态应为 DONE")
    void testMarkSubtaskCompleteAllDone() throws InterruptedException {
        ManufacturingTask task = new ManufacturingTask(1, 1, 2, 100);
        TeamMember member1 = new TeamMember(1, "Worker-1");
        TeamMember member2 = new TeamMember(2, "Worker-2");
        task.assignMember(member1);
        task.assignMember(member2);
        task.start();

        task.markSubtaskComplete();
        assertEquals(1, task.getCompletedSubtasks());
        assertEquals(ManufacturingTask.Status.IN_PROGRESS, task.getStatus());

        task.markSubtaskComplete();
        assertEquals(2, task.getCompletedSubtasks());
        assertEquals(ManufacturingTask.Status.DONE, task.getStatus());
        assertNotNull(task.getEndTime());
        assertTrue(member1.isAvailable());
        assertTrue(member2.isAvailable());
    }

    @Test
    @DisplayName("markSubtaskFailed：有子任务失败时状态应为 FAILED")
    void testMarkSubtaskFailed() throws InterruptedException {
        ManufacturingTask task = new ManufacturingTask(1, 1, 2, 100);
        TeamMember member1 = new TeamMember(1, "Worker-1");
        TeamMember member2 = new TeamMember(2, "Worker-2");
        task.assignMember(member1);
        task.assignMember(member2);
        task.start();

        task.markSubtaskComplete();
        task.markSubtaskFailed("error");

        assertEquals(1, task.getCompletedSubtasks());
        assertEquals(1, task.getFailedSubtasks());
        assertEquals(ManufacturingTask.Status.FAILED, task.getStatus());
        assertEquals("error", task.getFailureReason());
        assertTrue(member1.isAvailable());
        assertTrue(member2.isAvailable());
    }

    @Test
    @DisplayName("awaitCompletion：应等待所有子任务完成")
    void testAwaitCompletion() throws InterruptedException {
        ManufacturingTask task = new ManufacturingTask(1, 1, 2, 100);
        TeamMember member1 = new TeamMember(1, "Worker-1");
        TeamMember member2 = new TeamMember(2, "Worker-2");
        task.assignMember(member1);
        task.assignMember(member2);
        task.start();

        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> {
            try {
                task.awaitCompletion();
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        assertFalse(latch.await(100, TimeUnit.MILLISECONDS));

        task.markSubtaskComplete();
        task.markSubtaskComplete();

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        assertEquals(ManufacturingTask.Status.DONE, task.getStatus());
    }

    @Test
    @DisplayName("awaitCompletion(timeout)：超时时应返回 false")
    void testAwaitCompletionTimeout() throws InterruptedException {
        ManufacturingTask task = new ManufacturingTask(1, 1, 2, 100);
        TeamMember member1 = new TeamMember(1, "Worker-1");
        TeamMember member2 = new TeamMember(2, "Worker-2");
        task.assignMember(member1);
        task.assignMember(member2);
        task.start();

        boolean completed = task.awaitCompletion(100);
        assertFalse(completed);
        assertEquals(ManufacturingTask.Status.IN_PROGRESS, task.getStatus());
    }

    @Test
    @DisplayName("Status 枚举：应包含所有要求的状态")
    void testStatusEnum() {
        ManufacturingTask.Status[] values = ManufacturingTask.Status.values();
        assertEquals(4, values.length);
        assertEquals(ManufacturingTask.Status.PENDING, ManufacturingTask.Status.valueOf("PENDING"));
        assertEquals(ManufacturingTask.Status.IN_PROGRESS, ManufacturingTask.Status.valueOf("IN_PROGRESS"));
        assertEquals(ManufacturingTask.Status.DONE, ManufacturingTask.Status.valueOf("DONE"));
        assertEquals(ManufacturingTask.Status.FAILED, ManufacturingTask.Status.valueOf("FAILED"));
    }
}
