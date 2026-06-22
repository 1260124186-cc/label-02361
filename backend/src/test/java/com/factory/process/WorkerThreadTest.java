package com.factory.process;

import com.factory.model.ManufacturingTask;
import com.factory.model.TeamMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkerThread 单元测试")
class WorkerThreadTest {

    @Test
    @DisplayName("构造函数：参数无效应抛出 IllegalArgumentException")
    void testConstructorInvalid() {
        TeamMember member = new TeamMember(1, "Worker-1");
        ManufacturingTask task = new ManufacturingTask(1, 1, 1, 100);

        assertThrows(IllegalArgumentException.class, () -> new WorkerThread(null, task));
        assertThrows(IllegalArgumentException.class, () -> new WorkerThread(member, null));
        assertThrows(IllegalArgumentException.class, () -> new WorkerThread(member, task, -1));
    }

    @Test
    @DisplayName("构造函数：有效参数应正常创建")
    void testConstructorValid() {
        TeamMember member = new TeamMember(1, "Worker-1");
        ManufacturingTask task = new ManufacturingTask(1, 1, 1, 100);

        WorkerThread worker = new WorkerThread(member, task);
        assertSame(member, worker.getMember());
        assertSame(task, worker.getTask());
        assertEquals(100, worker.getWorkDurationMs());
    }

    @Test
    @DisplayName("构造函数：自定义工作时长应正常设置")
    void testConstructorCustomDuration() {
        TeamMember member = new TeamMember(1, "Worker-1");
        ManufacturingTask task = new ManufacturingTask(1, 1, 1, 100);

        WorkerThread worker = new WorkerThread(member, task, 200);
        assertEquals(200, worker.getWorkDurationMs());
    }

    @Test
    @DisplayName("run：正常执行应标记子任务完成")
    void testRunSuccess() throws InterruptedException {
        TeamMember member = new TeamMember(1, "Worker-1");
        ManufacturingTask task = new ManufacturingTask(1, 1, 1, 50);
        task.assignMember(member);
        task.start();

        WorkerThread worker = new WorkerThread(member, task, 50);
        Thread t = new Thread(worker, "test-worker");
        t.start();

        assertTrue(task.awaitCompletion(1000));
        assertEquals(1, task.getCompletedSubtasks());
        assertEquals(0, task.getFailedSubtasks());
        assertEquals(ManufacturingTask.Status.DONE, task.getStatus());
        assertTrue(member.isAvailable());
    }

    @Test
    @DisplayName("run：中断时应标记子任务失败")
    void testRunInterrupted() throws InterruptedException {
        TeamMember member = new TeamMember(1, "Worker-1");
        ManufacturingTask task = new ManufacturingTask(1, 1, 1, 500);
        task.assignMember(member);
        task.start();

        WorkerThread worker = new WorkerThread(member, task, 500);
        Thread t = new Thread(worker, "test-worker");
        t.start();

        Thread.sleep(50);
        t.interrupt();

        assertTrue(task.awaitCompletion(1000));
        assertEquals(0, task.getCompletedSubtasks());
        assertEquals(1, task.getFailedSubtasks());
        assertEquals(ManufacturingTask.Status.FAILED, task.getStatus());
        assertTrue(t.isInterrupted());
    }

    @Test
    @DisplayName("run：多个 WorkerThread 并发执行应正确计数")
    void testRunMultipleConcurrent() throws InterruptedException {
        int requiredPeople = 3;
        ManufacturingTask task = new ManufacturingTask(1, 1, requiredPeople, 50);

        CountDownLatch assignmentLatch = new CountDownLatch(requiredPeople);
        for (int i = 1; i <= requiredPeople; i++) {
            TeamMember member = new TeamMember(i, "Worker-" + i);
            task.assignMember(member);
            assignmentLatch.countDown();
        }
        assertTrue(assignmentLatch.await(1, TimeUnit.SECONDS));

        task.start();

        CountDownLatch startLatch = new CountDownLatch(1);
        for (TeamMember member : task.getAssignedMembers()) {
            WorkerThread worker = new WorkerThread(member, task, 50);
            new Thread(() -> {
                try {
                    startLatch.await();
                    worker.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "test-worker-" + member.getId()).start();
        }

        startLatch.countDown();

        assertTrue(task.awaitCompletion(1000));
        assertEquals(requiredPeople, task.getCompletedSubtasks());
        assertEquals(0, task.getFailedSubtasks());
        assertEquals(ManufacturingTask.Status.DONE, task.getStatus());
        for (TeamMember member : task.getAssignedMembers()) {
            assertTrue(member.isAvailable());
        }
    }
}
