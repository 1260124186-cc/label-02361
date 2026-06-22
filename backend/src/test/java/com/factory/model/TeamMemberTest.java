package com.factory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TeamMember 单元测试")
class TeamMemberTest {

    @Test
    @DisplayName("构造函数：id < 1 应抛出 IllegalArgumentException")
    void testConstructorInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> new TeamMember(0, "Worker"));
        assertThrows(IllegalArgumentException.class, () -> new TeamMember(-1, "Worker"));
    }

    @Test
    @DisplayName("构造函数：name 为 null 或空应抛出 IllegalArgumentException")
    void testConstructorInvalidName() {
        assertThrows(IllegalArgumentException.class, () -> new TeamMember(1, null));
        assertThrows(IllegalArgumentException.class, () -> new TeamMember(1, ""));
        assertThrows(IllegalArgumentException.class, () -> new TeamMember(1, "   "));
    }

    @Test
    @DisplayName("构造函数：有效参数应正常创建，初始状态为 AVAILABLE")
    void testConstructorValid() {
        TeamMember member = new TeamMember(1, "Worker-1");
        assertEquals(1, member.getId());
        assertEquals("Worker-1", member.getName());
        assertTrue(member.isAvailable());
        assertEquals(TeamMember.Status.AVAILABLE, member.getStatus());
        assertNull(member.getCurrentTask());
        assertTrue(member.getSkilledPIDs().isEmpty());
    }

    @Test
    @DisplayName("构造函数：带技能集应正常创建")
    void testConstructorWithSkills() {
        Set<Integer> skills = Set.of(1, 2, 3);
        TeamMember member = new TeamMember(1, "Worker-1", skills);
        assertEquals(skills, member.getSkilledPIDs());
    }

    @Test
    @DisplayName("isSkilledFor：空技能集表示全能，应返回 true")
    void testIsSkilledForAllPurpose() {
        TeamMember member = new TeamMember(1, "Worker-1");
        assertTrue(member.isSkilledFor(1));
        assertTrue(member.isSkilledFor(999));
    }

    @Test
    @DisplayName("isSkilledFor：有技能集时应正确判断")
    void testIsSkilledForSpecific() {
        TeamMember member = new TeamMember(1, "Worker-1", Set.of(1, 2));
        assertTrue(member.isSkilledFor(1));
        assertTrue(member.isSkilledFor(2));
        assertFalse(member.isSkilledFor(3));
    }

    @Test
    @DisplayName("addSkill/removeSkill：应正确修改技能集")
    void testAddRemoveSkill() {
        TeamMember member = new TeamMember(1, "Worker-1");
        assertTrue(member.isSkilledFor(5));

        member.addSkill(5);
        assertTrue(member.isSkilledFor(5));
        assertTrue(member.getSkilledPIDs().contains(5));

        member.removeSkill(5);
        assertFalse(member.getSkilledPIDs().contains(5));
    }

    @Test
    @DisplayName("assignTask：成员可用时应成功分配，状态变为 BUSY")
    void testAssignTaskSuccess() {
        TeamMember member = new TeamMember(1, "Worker-1");
        ManufacturingTask task = new ManufacturingTask(1, 1, 2, 100);

        member.assignTask(task);
        assertFalse(member.isAvailable());
        assertEquals(TeamMember.Status.BUSY, member.getStatus());
        assertSame(task, member.getCurrentTask());
    }

    @Test
    @DisplayName("assignTask：成员忙碌时应抛出 IllegalStateException")
    void testAssignTaskWhenBusy() {
        TeamMember member = new TeamMember(1, "Worker-1");
        ManufacturingTask task1 = new ManufacturingTask(1, 1, 2, 100);
        ManufacturingTask task2 = new ManufacturingTask(2, 2, 2, 100);

        member.assignTask(task1);
        assertThrows(IllegalStateException.class, () -> member.assignTask(task2));
    }

    @Test
    @DisplayName("completeTask：应将状态恢复为 AVAILABLE")
    void testCompleteTask() {
        TeamMember member = new TeamMember(1, "Worker-1");
        ManufacturingTask task = new ManufacturingTask(1, 1, 2, 100);

        member.assignTask(task);
        assertFalse(member.isAvailable());

        member.completeTask();
        assertTrue(member.isAvailable());
        assertEquals(TeamMember.Status.AVAILABLE, member.getStatus());
        assertNull(member.getCurrentTask());
    }

    @Test
    @DisplayName("getSkilledPIDs：应返回防御性拷贝")
    void testGetSkilledPIDsDefensiveCopy() {
        TeamMember member = new TeamMember(1, "Worker-1", Set.of(1, 2));
        Set<Integer> skills = member.getSkilledPIDs();
        skills.add(3);
        assertFalse(member.getSkilledPIDs().contains(3));
    }
}
