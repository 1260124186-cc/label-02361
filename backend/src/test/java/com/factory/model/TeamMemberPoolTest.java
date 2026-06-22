package com.factory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TeamMemberPool 单元测试")
class TeamMemberPoolTest {

    @Test
    @DisplayName("构造函数：参数无效应抛出 IllegalArgumentException")
    void testConstructorInvalid() {
        assertThrows(IllegalArgumentException.class, () -> new TeamMemberPool(null));
        assertThrows(IllegalArgumentException.class, () -> new TeamMemberPool(new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> {
            List<TeamMember> members = List.of(new TeamMember(1, "Worker-1"));
            new TeamMemberPool(members, null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            List<TeamMember> members = List.of(new TeamMember(1, "Worker-1"));
            new TeamMemberPool(members, CapacityStrategy.WAIT, -1);
        });
    }

    @Test
    @DisplayName("createDefault：应创建指定大小的成员池")
    void testCreateDefault() {
        TeamMemberPool pool = TeamMemberPool.createDefault(5);
        assertEquals(5, pool.getTotalCount());
        assertEquals(0, pool.getBusyCount());
        assertEquals(5, pool.getAvailableCountForPID(1));
        assertEquals(CapacityStrategy.WAIT, pool.getCapacityStrategy());
    }

    @Test
    @DisplayName("createDefault：size < 1 应抛出 IllegalArgumentException")
    void testCreateDefaultInvalidSize() {
        assertThrows(IllegalArgumentException.class, () -> TeamMemberPool.createDefault(0));
        assertThrows(IllegalArgumentException.class, () -> TeamMemberPool.createDefault(-1));
    }

    @Test
    @DisplayName("getAvailableMembersForPID：成员足够时应返回所需数量")
    void testGetAvailableMembersForPIDEnough() throws InterruptedException {
        TeamMemberPool pool = TeamMemberPool.createDefault(5);
        List<TeamMember> members = pool.getAvailableMembersForPID(1, 3);
        assertEquals(3, members.size());

        ManufacturingTask task = new ManufacturingTask(1, 1, 3, 100);
        for (TeamMember member : members) {
            task.assignMember(member);
        }

        assertEquals(3, pool.getBusyCount());
        assertEquals(2, pool.getAvailableCountForPID(1));
    }

    @Test
    @DisplayName("getAvailableMembersForPID：ALERT 策略成员不足时应记录告警并返回可用成员")
    void testGetAvailableMembersForPIDAlertStrategy() throws InterruptedException {
        TeamMemberPool pool = TeamMemberPool.createDefault(2, CapacityStrategy.ALERT, 0);

        TeamMember busyMember = pool.getAllMembers().get(0);
        busyMember.assignTask(new ManufacturingTask(1, 1, 1, 100));

        assertEquals(0, pool.getAlertCount());
        List<TeamMember> members = pool.getAvailableMembersForPID(1, 3);
        assertEquals(1, members.size());
        assertEquals(1, pool.getAlertCount());
    }

    @Test
    @DisplayName("getAvailableMembersForPID：FAIL 策略成员不足时应抛出 IllegalStateException")
    void testGetAvailableMembersForPIDFailStrategy() {
        TeamMemberPool pool = TeamMemberPool.createDefault(2, CapacityStrategy.FAIL, 0);

        TeamMember busyMember = pool.getAllMembers().get(0);
        busyMember.assignTask(new ManufacturingTask(1, 1, 1, 100));

        assertThrows(IllegalStateException.class, () -> pool.getAvailableMembersForPID(1, 3));
        assertEquals(1, pool.getAlertCount());
    }

    @Test
    @DisplayName("getAvailableMembersForPID：WAIT 策略带超时应在超时后返回")
    void testGetAvailableMembersForPIDWaitWithTimeout() throws InterruptedException {
        TeamMemberPool pool = TeamMemberPool.createDefault(1, CapacityStrategy.WAIT, 300);

        long startTime = System.currentTimeMillis();
        List<TeamMember> members = pool.getAvailableMembersForPID(1, 2);
        long elapsed = System.currentTimeMillis() - startTime;

        assertTrue(elapsed >= 250, "Elapsed time " + elapsed + " should be >= 250ms");
        assertEquals(1, members.size());
        assertEquals(1, pool.getAlertCount());
    }

    @Test
    @DisplayName("getAvailableMembersForPID：技能匹配时应只返回具备技能的成员")
    void testGetAvailableMembersForPIDWithSkills() throws InterruptedException {
        List<TeamMember> members = new ArrayList<>();
        members.add(new TeamMember(1, "Worker-1", Set.of(1, 2)));
        members.add(new TeamMember(2, "Worker-2", Set.of(1)));
        members.add(new TeamMember(3, "Worker-3", Set.of(3)));
        members.add(new TeamMember(4, "Worker-4"));

        TeamMemberPool pool = new TeamMemberPool(members, CapacityStrategy.ALERT, 0);

        List<TeamMember> availableForPID1 = pool.getAvailableMembersForPID(1, 10);
        assertEquals(3, availableForPID1.size());

        List<TeamMember> availableForPID3 = pool.getAvailableMembersForPID(3, 10);
        assertEquals(2, availableForPID3.size());
    }

    @Test
    @DisplayName("getAvailableCountForPID：应正确统计可用成员数")
    void testGetAvailableCountForPID() {
        List<TeamMember> members = new ArrayList<>();
        members.add(new TeamMember(1, "Worker-1", Set.of(1)));
        members.add(new TeamMember(2, "Worker-2", Set.of(1)));
        members.add(new TeamMember(3, "Worker-3", Set.of(2)));

        TeamMemberPool pool = new TeamMemberPool(members);
        assertEquals(2, pool.getAvailableCountForPID(1));
        assertEquals(1, pool.getAvailableCountForPID(2));

        members.get(0).assignTask(new ManufacturingTask(1, 1, 1, 100));
        assertEquals(1, pool.getAvailableCountForPID(1));
    }

    @Test
    @DisplayName("addMember：应成功添加新成员")
    void testAddMember() {
        TeamMemberPool pool = TeamMemberPool.createDefault(2);
        assertEquals(2, pool.getTotalCount());

        pool.addMember(new TeamMember(3, "Worker-3"));
        assertEquals(3, pool.getTotalCount());
    }

    @Test
    @DisplayName("resetAlertCount：应重置告警计数")
    void testResetAlertCount() throws InterruptedException {
        TeamMemberPool pool = TeamMemberPool.createDefault(1, CapacityStrategy.ALERT, 0);
        pool.getAvailableMembersForPID(1, 2);
        assertEquals(1, pool.getAlertCount());

        pool.resetAlertCount();
        assertEquals(0, pool.getAlertCount());
    }

    @Test
    @DisplayName("getAllMembers：应返回不可修改的列表")
    void testGetAllMembers() {
        TeamMemberPool pool = TeamMemberPool.createDefault(2);
        List<TeamMember> members = pool.getAllMembers();
        assertThrows(UnsupportedOperationException.class, () -> members.add(new TeamMember(3, "Worker-3")));
    }

    @Test
    @DisplayName("logStatus：不应抛出异常")
    void testLogStatus() {
        TeamMemberPool pool = TeamMemberPool.createDefault(3);
        assertDoesNotThrow(pool::logStatus);
    }
}
