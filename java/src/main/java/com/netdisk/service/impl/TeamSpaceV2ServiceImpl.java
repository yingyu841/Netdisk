package com.netdisk.service.impl;

import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.config.AppProperties;
import com.netdisk.mapper.UserMapper;
import com.netdisk.pojo.entity.User;
import com.netdisk.security.FileAccessSigner;
import com.netdisk.service.TeamSpaceV2Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TeamSpaceV2ServiceImpl implements TeamSpaceV2Service {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final UserMapper userMapper;
    private final AppProperties appProperties;

    public TeamSpaceV2ServiceImpl(JdbcTemplate jdbcTemplate, UserMapper userMapper, AppProperties appProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.userMapper = userMapper;
        this.appProperties = appProperties;
    }

    @Override
    @Transactional
    public Map<String, Object> createGroup(String userUuid, Map<String, Object> request) {
        User user = requireUser(userUuid);
        String name = trim(str(request, "name"));
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "name必填");
        }
        String description = trim(str(request, "description"));
        String visibility = normalizeVisibility(str(request, "visibility"));
        Long quotaBytes = longVal(request, "quotaBytes");
        if (quotaBytes == null || quotaBytes.longValue() <= 0L) {
            quotaBytes = Long.valueOf(107374182400L);
        }

        String groupUuid = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO spaces (space_uuid, owner_user_id, name, space_type, status, visibility, description, quota_bytes, used_bytes, max_members, is_archived, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'team', 1, ?, ?, ?, 0, 200, 0, NOW(), NOW())",
                groupUuid, user.getId(), name, visibility, emptyToNull(description), quotaBytes
        );

        Long spaceId = jdbcTemplate.queryForObject("SELECT id FROM spaces WHERE space_uuid = ?", Long.class, groupUuid);
        jdbcTemplate.update(
                "INSERT INTO space_members (space_id, user_id, role_code, status, joined_at, invited_at, created_at, updated_at) " +
                        "VALUES (?, ?, 'owner', 1, NOW(), NOW(), NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE role_code = 'owner', status = 1, updated_at = NOW()",
                spaceId, user.getId()
        );

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", groupUuid);
        data.put("name", name);
        data.put("visibility", visibility);
        data.put("role", "owner");
        data.put("memberCount", 1);
        data.put("usedBytes", 0L);
        data.put("quotaBytes", quotaBytes);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listMyGroups(String userUuid, String keyword, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        int[] paged = normalizePage(page, pageSize);
        int p = paged[0];
        int size = paged[1];
        int offset = (p - 1) * size;
        String kw = normalizeLikeKeyword(keyword);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) " +
                        "FROM spaces s " +
                        "JOIN space_members sm ON sm.space_id = s.id AND sm.user_id = ? AND sm.status = 1 " +
                        "WHERE s.space_type = 'team' AND s.status = 1 AND s.is_archived = 0 " +
                        "AND (? IS NULL OR s.name LIKE ?)",
                Long.class, user.getId(), kw, kw
        );

        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT s.id, s.space_uuid, s.name, s.visibility, s.used_bytes, s.quota_bytes, s.created_at, sm.role_code " +
                        "FROM spaces s " +
                        "JOIN space_members sm ON sm.space_id = s.id AND sm.user_id = ? AND sm.status = 1 " +
                        "WHERE s.space_type = 'team' AND s.status = 1 AND s.is_archived = 0 " +
                        "AND (? IS NULL OR s.name LIKE ?) " +
                        "ORDER BY s.created_at DESC, s.id DESC LIMIT ?, ?",
                mapRowMapper(),
                user.getId(), kw, kw, offset, size
        );

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Long sid = longObj(row.get("id"));
            Long memberCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM space_members WHERE space_id = ? AND status = 1",
                    Long.class,
                    sid
            );
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", str(row.get("space_uuid")));
            item.put("name", str(row.get("name")));
            item.put("visibility", str(row.get("visibility")));
            item.put("role", str(row.get("role_code")));
            item.put("memberCount", memberCount == null ? 0L : memberCount.longValue());
            item.put("usedBytes", longObj(row.get("used_bytes")) == null ? 0L : longObj(row.get("used_bytes")));
            item.put("quotaBytes", longObj(row.get("quota_bytes")) == null ? 0L : longObj(row.get("quota_bytes")));
            item.put("createdAt", dt(row.get("created_at")));
            items.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getGroupDetail(String userUuid, String groupId) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        return buildGroupDetail(group);
    }

    @Override
    @Transactional
    public Map<String, Object> updateGroup(String userUuid, String groupId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        requireOwnerOrEditor(group);

        String name = trim(str(request, "name"));
        String description = trim(str(request, "description"));
        String visibility = trim(str(request, "visibility"));
        Long quotaBytes = longVal(request, "quotaBytes");
        Integer maxMembers = intVal(request, "maxMembers");
        String avatarUrl = trim(str(request, "avatarUrl"));

        List<String> clauses = new ArrayList<String>();
        List<Object> args = new ArrayList<Object>();
        if (!name.isEmpty()) {
            clauses.add("name = ?");
            args.add(name);
        }
        if (request.containsKey("description")) {
            clauses.add("description = ?");
            args.add(emptyToNull(description));
        }
        if (!visibility.isEmpty()) {
            clauses.add("visibility = ?");
            args.add(normalizeVisibility(visibility));
        }
        if (quotaBytes != null && quotaBytes.longValue() > 0L) {
            clauses.add("quota_bytes = ?");
            args.add(quotaBytes);
        }
        if (maxMembers != null && maxMembers.intValue() > 0) {
            clauses.add("max_members = ?");
            args.add(maxMembers);
        }
        if (request.containsKey("avatarUrl")) {
            clauses.add("avatar_url = ?");
            args.add(emptyToNull(avatarUrl));
        }
        if (clauses.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "至少提供一个可更新字段");
        }
        clauses.add("updated_at = NOW()");
        args.add(group.get("id"));

        jdbcTemplate.update("UPDATE spaces SET " + joinClauses(clauses) + " WHERE id = ?", args.toArray());
        Map<String, Object> latest = requireGroupAndMembership(groupId, user.getId());
        return buildGroupDetail(latest);
    }

    @Override
    @Transactional
    public Map<String, Object> archiveGroup(String userUuid, String groupId) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        requireOwnerOrEditor(group);
        jdbcTemplate.update("UPDATE spaces SET is_archived = 1, updated_at = NOW() WHERE id = ?", group.get("id"));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", group.get("space_uuid"));
        data.put("archived", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> deleteGroup(String userUuid, String groupId) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        if (!"owner".equalsIgnoreCase(str(group.get("role_code")))) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "仅群主可解散群组");
        }
        Long gid = longObj(group.get("id"));
        jdbcTemplate.update("UPDATE space_members SET status = 2, removed_at = NOW(), updated_at = NOW() WHERE space_id = ? AND status = 1", gid);
        jdbcTemplate.update("UPDATE spaces SET status = 2, is_archived = 1, updated_at = NOW() WHERE id = ?", gid);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", group.get("space_uuid"));
        data.put("deleted", true);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listMembers(String userUuid, String groupId, String keyword, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        Long gid = longObj(group.get("id"));
        int[] paged = normalizePage(page, pageSize);
        int p = paged[0];
        int size = paged[1];
        int offset = (p - 1) * size;
        String kw = normalizeLikeKeyword(keyword);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM space_members sm " +
                        "JOIN users u ON u.id = sm.user_id " +
                        "WHERE sm.space_id = ? AND sm.status = 1 " +
                        "AND (? IS NULL OR u.nickname LIKE ? OR u.email LIKE ?)",
                Long.class, gid, kw, kw, kw
        );
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT u.user_uuid, u.nickname, u.email, u.avatar_url, sm.role_code, sm.status, sm.joined_at " +
                        "FROM space_members sm " +
                        "JOIN users u ON u.id = sm.user_id " +
                        "WHERE sm.space_id = ? AND sm.status = 1 " +
                        "AND (? IS NULL OR u.nickname LIKE ? OR u.email LIKE ?) " +
                        "ORDER BY sm.joined_at ASC, sm.id ASC LIMIT ?, ?",
                mapRowMapper(), gid, kw, kw, kw, offset, size
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("userId", str(row.get("user_uuid")));
            item.put("nickname", str(row.get("nickname")));
            item.put("email", str(row.get("email")));
            item.put("avatarUrl", str(row.get("avatar_url")));
            item.put("role", str(row.get("role_code")));
            item.put("status", intObj(row.get("status")) != null && intObj(row.get("status")) == 1 ? "active" : "removed");
            item.put("joinedAt", dt(row.get("joined_at")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> inviteMember(String userUuid, String groupId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        requireOwnerOrEditor(group);
        Long gid = longObj(group.get("id"));

        String role = normalizeRole(str(request, "role"), false);
        String inviteeUserId = trim(str(request, "inviteeUserId"));
        String inviteeEmail = trim(str(request, "inviteeEmail")).toLowerCase(Locale.ROOT);
        Integer expireHours = intVal(request, "expireHours");
        if (expireHours == null || expireHours.intValue() <= 0) {
            expireHours = Integer.valueOf(72);
        }
        if (inviteeUserId.isEmpty() == inviteeEmail.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "inviteeUserId 或 inviteeEmail 必须且只能填写一个");
        }

        Long inviteeId = null;
        if (!inviteeUserId.isEmpty()) {
            User invitee = userMapper.findByUserUuid(inviteeUserId);
            if (invitee == null) {
                throw new BizException(ErrorCode.NOT_FOUND, 404, "被邀请用户不存在");
            }
            inviteeId = invitee.getId();
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM space_members WHERE space_id = ? AND user_id = ? AND status = 1",
                    Integer.class, gid, inviteeId
            );
            if (exists != null && exists.intValue() > 0) {
                throw new BizException(ErrorCode.CONFLICT, 409, "该用户已是群成员");
            }
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        String inviteUuid = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO space_invites (invite_uuid, space_id, inviter_user_id, invitee_user_id, invitee_email, role_code, token_hash, status, expired_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', DATE_ADD(NOW(), INTERVAL ? HOUR), NOW(), NOW())",
                inviteUuid, gid, user.getId(), inviteeId, emptyToNull(inviteeEmail), role, sha256Hex(token), expireHours
        );

        String expiredAt = jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(expired_at, '%Y-%m-%d %H:%i:%s') FROM space_invites WHERE invite_uuid = ?",
                String.class,
                inviteUuid
        );

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("inviteId", inviteUuid);
        data.put("inviteToken", token);
        data.put("expiredAt", expiredAt);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listInvites(String userUuid, String groupId, String status, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        requireOwnerOrEditor(group);
        Long gid = longObj(group.get("id"));
        int[] paged = normalizePage(page, pageSize);
        int p = paged[0];
        int size = paged[1];
        int offset = (p - 1) * size;
        String statusFilter = trim(status).toLowerCase(Locale.ROOT);
        if (!statusFilter.isEmpty() && !"pending".equals(statusFilter) && !"accepted".equals(statusFilter)
                && !"rejected".equals(statusFilter) && !"expired".equals(statusFilter) && !"cancelled".equals(statusFilter)) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "status不合法");
        }
        String statusSql = statusFilter.isEmpty() ? null : statusFilter;

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM space_invites si WHERE si.space_id = ? AND (? IS NULL OR si.status = ?)",
                Long.class, gid, statusSql, statusSql
        );
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT si.invite_uuid, si.role_code, si.status, si.expired_at, si.created_at, u.user_uuid AS invitee_user_uuid, si.invitee_email " +
                        "FROM space_invites si " +
                        "LEFT JOIN users u ON u.id = si.invitee_user_id " +
                        "WHERE si.space_id = ? AND (? IS NULL OR si.status = ?) " +
                        "ORDER BY si.created_at DESC, si.id DESC LIMIT ?, ?",
                mapRowMapper(), gid, statusSql, statusSql, offset, size
        );

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("inviteId", str(row.get("invite_uuid")));
            item.put("inviteeUserId", str(row.get("invitee_user_uuid")));
            item.put("inviteeEmail", str(row.get("invitee_email")));
            item.put("role", str(row.get("role_code")));
            item.put("status", str(row.get("status")));
            item.put("expiredAt", dt(row.get("expired_at")));
            item.put("createdAt", dt(row.get("created_at")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> cancelInvite(String userUuid, String groupId, String inviteId) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        requireOwnerOrEditor(group);
        Long gid = longObj(group.get("id"));

        int changed = jdbcTemplate.update(
                "UPDATE space_invites SET status = 'cancelled', updated_at = NOW() " +
                        "WHERE invite_uuid = ? AND space_id = ? AND status = 'pending'",
                trim(inviteId), gid
        );
        if (changed <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "邀请不存在或不可取消");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("inviteId", trim(inviteId));
        data.put("cancelled", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> acceptInvite(String userUuid, String inviteToken) {
        User user = requireUser(userUuid);
        Map<String, Object> invite = requirePendingInvite(inviteToken);

        Long inviteeUserId = longObj(invite.get("invitee_user_id"));
        String inviteeEmail = trim(str(invite.get("invitee_email"))).toLowerCase(Locale.ROOT);
        if (inviteeUserId != null && !inviteeUserId.equals(user.getId())) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "该邀请不属于当前用户");
        }
        if (inviteeUserId == null && !inviteeEmail.isEmpty()) {
            String userEmail = trim(user.getEmail()).toLowerCase(Locale.ROOT);
            if (!inviteeEmail.equals(userEmail)) {
                throw new BizException(ErrorCode.FORBIDDEN, 403, "该邀请不属于当前用户");
            }
        }

        Long gid = longObj(invite.get("space_id"));
        String role = normalizeRole(str(invite.get("role_code")), false);
        jdbcTemplate.update(
                "INSERT INTO space_members (space_id, user_id, role_code, status, joined_at, invited_at, inviter_user_id, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 1, NOW(), NOW(), ?, NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE role_code = VALUES(role_code), status = 1, joined_at = NOW(), updated_at = NOW()",
                gid, user.getId(), role, invite.get("inviter_user_id")
        );
        jdbcTemplate.update(
                "UPDATE space_invites SET status = 'accepted', accepted_at = NOW(), updated_at = NOW() WHERE id = ?",
                invite.get("id")
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("inviteId", invite.get("invite_uuid"));
        data.put("accepted", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> rejectInvite(String userUuid, String inviteToken) {
        User user = requireUser(userUuid);
        Map<String, Object> invite = requirePendingInvite(inviteToken);
        Long inviteeUserId = longObj(invite.get("invitee_user_id"));
        if (inviteeUserId != null && !inviteeUserId.equals(user.getId())) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "该邀请不属于当前用户");
        }
        jdbcTemplate.update(
                "UPDATE space_invites SET status = 'rejected', rejected_at = NOW(), updated_at = NOW() WHERE id = ?",
                invite.get("id")
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("inviteId", invite.get("invite_uuid"));
        data.put("rejected", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> updateMemberRole(String userUuid, String groupId, String targetUserId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        requireOwner(group);

        User target = userMapper.findByUserUuid(trim(targetUserId));
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "成员不存在");
        }
        String role = normalizeRole(str(request, "role"), true);
        if ("owner".equals(role) && !target.getId().equals(user.getId())) {
            jdbcTemplate.update("UPDATE space_members SET role_code = 'editor', updated_at = NOW() WHERE space_id = ? AND user_id = ? AND status = 1", group.get("id"), user.getId());
        }
        int changed = jdbcTemplate.update(
                "UPDATE space_members SET role_code = ?, updated_at = NOW() WHERE space_id = ? AND user_id = ? AND status = 1",
                role, group.get("id"), target.getId()
        );
        if (changed <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "成员不存在或已移除");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("userId", targetUserId);
        data.put("role", role);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> removeMember(String userUuid, String groupId, String targetUserId) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        requireOwnerOrEditor(group);
        User target = userMapper.findByUserUuid(trim(targetUserId));
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "成员不存在");
        }
        if (target.getId().equals(longObj(group.get("owner_user_id")))) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "不能移除群主");
        }
        int changed = jdbcTemplate.update(
                "UPDATE space_members SET status = 2, removed_at = NOW(), updated_at = NOW() WHERE space_id = ? AND user_id = ? AND status = 1",
                group.get("id"), target.getId()
        );
        if (changed <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "成员不存在或已移除");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("userId", targetUserId);
        data.put("removed", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> createJoinRequest(String userUuid, String groupId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroup(groupId);
        if (!"public".equalsIgnoreCase(str(group.get("visibility")))) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "仅公开群组允许申请加入");
        }

        Integer memberExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM space_members WHERE space_id = ? AND user_id = ? AND status = 1",
                Integer.class, group.get("id"), user.getId()
        );
        if (memberExists != null && memberExists.intValue() > 0) {
            throw new BizException(ErrorCode.CONFLICT, 409, "你已经是群成员");
        }

        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM space_join_requests WHERE space_id = ? AND applicant_user_id = ? AND status = 'pending'",
                Integer.class, group.get("id"), user.getId()
        );
        if (pending != null && pending.intValue() > 0) {
            throw new BizException(ErrorCode.CONFLICT, 409, "已有待处理申请");
        }

        jdbcTemplate.update(
                "INSERT INTO space_join_requests (request_uuid, space_id, applicant_user_id, message, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'pending', NOW(), NOW())",
                UUID.randomUUID().toString(), group.get("id"), user.getId(), emptyToNull(trim(str(request, "message")))
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("submitted", true);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listMyJoinRequests(String userUuid, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        int[] paged = normalizePage(page, pageSize);
        int p = paged[0];
        int size = paged[1];
        int offset = (p - 1) * size;

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM space_join_requests WHERE applicant_user_id = ?",
                Long.class, user.getId()
        );
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT jr.request_uuid, jr.status, jr.message, jr.created_at, s.space_uuid, s.name AS group_name " +
                        "FROM space_join_requests jr " +
                        "JOIN spaces s ON s.id = jr.space_id " +
                        "WHERE jr.applicant_user_id = ? " +
                        "ORDER BY jr.created_at DESC, jr.id DESC LIMIT ?, ?",
                mapRowMapper(), user.getId(), offset, size
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("requestId", str(row.get("request_uuid")));
            item.put("groupId", str(row.get("space_uuid")));
            item.put("groupName", str(row.get("group_name")));
            item.put("status", str(row.get("status")));
            item.put("message", str(row.get("message")));
            item.put("createdAt", dt(row.get("created_at")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listGroupJoinRequests(String userUuid, String groupId, String status, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        requireOwnerOrEditor(group);
        int[] paged = normalizePage(page, pageSize);
        int p = paged[0];
        int size = paged[1];
        int offset = (p - 1) * size;
        String statusFilter = trim(status);
        String statusSql = statusFilter.isEmpty() ? null : statusFilter;

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM space_join_requests WHERE space_id = ? AND (? IS NULL OR status = ?)",
                Long.class, group.get("id"), statusSql, statusSql
        );
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT jr.request_uuid, jr.message, jr.status, jr.created_at, u.user_uuid, u.nickname, u.email " +
                        "FROM space_join_requests jr " +
                        "JOIN users u ON u.id = jr.applicant_user_id " +
                        "WHERE jr.space_id = ? AND (? IS NULL OR jr.status = ?) " +
                        "ORDER BY jr.created_at DESC, jr.id DESC LIMIT ?, ?",
                mapRowMapper(), group.get("id"), statusSql, statusSql, offset, size
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("requestId", str(row.get("request_uuid")));
            item.put("userId", str(row.get("user_uuid")));
            item.put("nickname", str(row.get("nickname")));
            item.put("email", str(row.get("email")));
            item.put("message", str(row.get("message")));
            item.put("status", str(row.get("status")));
            item.put("createdAt", dt(row.get("created_at")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> approveJoinRequest(String userUuid, String groupId, String requestId) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        requireOwnerOrEditor(group);
        Map<String, Object> req = requireJoinRequest(group.get("id"), requestId);

        jdbcTemplate.update(
                "INSERT INTO space_members (space_id, user_id, role_code, status, joined_at, created_at, updated_at) " +
                        "VALUES (?, ?, 'viewer', 1, NOW(), NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE status = 1, role_code = IF(role_code='owner', role_code, 'viewer'), updated_at = NOW()",
                group.get("id"), req.get("applicant_user_id")
        );
        jdbcTemplate.update(
                "UPDATE space_join_requests SET status = 'approved', reviewer_user_id = ?, reviewed_at = NOW(), updated_at = NOW() WHERE id = ?",
                user.getId(), req.get("id")
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("requestId", requestId);
        data.put("approved", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> rejectJoinRequest(String userUuid, String groupId, String requestId) {
        User user = requireUser(userUuid);
        Map<String, Object> group = requireGroupAndMembership(groupId, user.getId());
        requireOwnerOrEditor(group);
        Map<String, Object> req = requireJoinRequest(group.get("id"), requestId);
        jdbcTemplate.update(
                "UPDATE space_join_requests SET status = 'rejected', reviewer_user_id = ?, reviewed_at = NOW(), updated_at = NOW() WHERE id = ?",
                user.getId(), req.get("id")
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("requestId", requestId);
        data.put("rejected", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> favoriteResource(String userUuid, String resourceId) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResource(user.getId(), resourceId);
        jdbcTemplate.update(
                "INSERT INTO user_favorites (user_id, resource_id, created_at) VALUES (?, ?, NOW()) ON DUPLICATE KEY UPDATE created_at = created_at",
                user.getId(), resource.get("id")
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("resourceId", resourceId);
        data.put("favorited", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> unfavoriteResource(String userUuid, String resourceId) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResource(user.getId(), resourceId);
        jdbcTemplate.update("DELETE FROM user_favorites WHERE user_id = ? AND resource_id = ?", user.getId(), resource.get("id"));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("resourceId", resourceId);
        data.put("favorited", false);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listMyFavorites(String userUuid, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        int[] paged = normalizePage(page, pageSize);
        int p = paged[0];
        int size = paged[1];
        int offset = (p - 1) * size;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM user_favorites uf JOIN resources r ON r.id = uf.resource_id WHERE uf.user_id = ? AND r.is_deleted = 0",
                Long.class, user.getId()
        );
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT r.resource_uuid, r.resource_type, r.name, r.extension, r.size_bytes, r.updated_at, uf.created_at AS favorited_at " +
                        "FROM user_favorites uf " +
                        "JOIN resources r ON r.id = uf.resource_id " +
                        "WHERE uf.user_id = ? AND r.is_deleted = 0 " +
                        "ORDER BY uf.created_at DESC, uf.id DESC LIMIT ?, ?",
                mapRowMapper(), user.getId(), offset, size
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("resourceId", str(row.get("resource_uuid")));
            item.put("type", str(row.get("resource_type")));
            item.put("name", str(row.get("name")));
            item.put("extension", str(row.get("extension")));
            item.put("sizeBytes", longObj(row.get("size_bytes")) == null ? 0L : longObj(row.get("size_bytes")));
            item.put("updatedAt", dt(row.get("updated_at")));
            item.put("favoritedAt", dt(row.get("favorited_at")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> createTag(String userUuid, Map<String, Object> request) {
        User user = requireUser(userUuid);
        String name = trim(str(request, "name"));
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "name必填");
        }
        String color = trim(str(request, "color"));
        try {
            jdbcTemplate.update(
                    "INSERT INTO tags (tag_uuid, owner_user_id, name, color, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                    UUID.randomUUID().toString(), user.getId(), name, emptyToNull(color)
            );
        } catch (Exception ex) {
            throw new BizException(ErrorCode.CONFLICT, 409, "标签名称重复");
        }
        Map<String, Object> row = jdbcTemplate.queryForObject(
                "SELECT tag_uuid, name, color, created_at FROM tags WHERE owner_user_id = ? AND name = ? ORDER BY id DESC LIMIT 1",
                mapRowMapper(), user.getId(), name
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", row.get("tag_uuid"));
        data.put("name", row.get("name"));
        data.put("color", row.get("color"));
        data.put("createdAt", dt(row.get("created_at")));
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listTags(String userUuid) {
        User user = requireUser(userUuid);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT t.tag_uuid, t.name, t.color, t.created_at, COUNT(rt.id) AS resource_count " +
                        "FROM tags t " +
                        "LEFT JOIN resource_tags rt ON rt.tag_id = t.id " +
                        "WHERE t.owner_user_id = ? " +
                        "GROUP BY t.id, t.tag_uuid, t.name, t.color, t.created_at " +
                        "ORDER BY t.created_at DESC, t.id DESC",
                mapRowMapper(), user.getId()
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", row.get("tag_uuid"));
            item.put("name", row.get("name"));
            item.put("color", row.get("color"));
            item.put("resourceCount", longObj(row.get("resource_count")) == null ? 0L : longObj(row.get("resource_count")));
            item.put("createdAt", dt(row.get("created_at")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> updateTag(String userUuid, String tagId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> tag = requireOwnedTag(user.getId(), tagId);
        String name = trim(str(request, "name"));
        String color = trim(str(request, "color"));
        if (name.isEmpty() && !request.containsKey("color")) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "至少提供name或color");
        }
        List<String> sets = new ArrayList<String>();
        List<Object> args = new ArrayList<Object>();
        if (!name.isEmpty()) {
            sets.add("name = ?");
            args.add(name);
        }
        if (request.containsKey("color")) {
            sets.add("color = ?");
            args.add(emptyToNull(color));
        }
        sets.add("updated_at = NOW()");
        args.add(tag.get("id"));
        jdbcTemplate.update("UPDATE tags SET " + joinClauses(sets) + " WHERE id = ?", args.toArray());
        Map<String, Object> latest = requireOwnedTag(user.getId(), tagId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", latest.get("tag_uuid"));
        data.put("name", latest.get("name"));
        data.put("color", latest.get("color"));
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> deleteTag(String userUuid, String tagId) {
        User user = requireUser(userUuid);
        Map<String, Object> tag = requireOwnedTag(user.getId(), tagId);
        jdbcTemplate.update("DELETE FROM resource_tags WHERE tag_id = ?", tag.get("id"));
        jdbcTemplate.update("DELETE FROM tags WHERE id = ?", tag.get("id"));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", tagId);
        data.put("deleted", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> addTagToResource(String userUuid, String resourceId, String tagId) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResource(user.getId(), resourceId);
        Map<String, Object> tag = requireOwnedTag(user.getId(), tagId);
        jdbcTemplate.update(
                "INSERT INTO resource_tags (resource_id, tag_id, created_at) VALUES (?, ?, NOW()) ON DUPLICATE KEY UPDATE created_at = created_at",
                resource.get("id"), tag.get("id")
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("resourceId", resourceId);
        data.put("tagId", tagId);
        data.put("attached", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> removeTagFromResource(String userUuid, String resourceId, String tagId) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResource(user.getId(), resourceId);
        Map<String, Object> tag = requireOwnedTag(user.getId(), tagId);
        jdbcTemplate.update("DELETE FROM resource_tags WHERE resource_id = ? AND tag_id = ?", resource.get("id"), tag.get("id"));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("resourceId", resourceId);
        data.put("tagId", tagId);
        data.put("attached", false);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listResourceTags(String userUuid, String resourceId) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResource(user.getId(), resourceId);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT t.tag_uuid, t.name, t.color, rt.created_at " +
                        "FROM resource_tags rt " +
                        "JOIN tags t ON t.id = rt.tag_id " +
                        "WHERE rt.resource_id = ? AND t.owner_user_id = ? " +
                        "ORDER BY rt.created_at DESC, rt.id DESC",
                mapRowMapper(), resource.get("id"), user.getId()
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", row.get("tag_uuid"));
            item.put("name", row.get("name"));
            item.put("color", row.get("color"));
            item.put("createdAt", dt(row.get("created_at")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> listResourceVersions(String userUuid, String resourceId, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResourceDetail(user.getId(), resourceId);
        int[] paged = normalizePage(page, pageSize);
        int offset = (paged[0] - 1) * paged[1];
        ensureCurrentVersionSnapshot(resource, user.getId());

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM resource_versions WHERE resource_id = ?",
                Long.class, resource.get("id")
        );
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT rv.version_no, rv.size_bytes, rv.sha256, rv.change_summary, rv.created_at, u.user_uuid, u.nickname " +
                        "FROM resource_versions rv " +
                        "JOIN users u ON u.id = rv.uploader_user_id " +
                        "WHERE rv.resource_id = ? ORDER BY rv.version_no DESC LIMIT ?, ?",
                mapRowMapper(), resource.get("id"), offset, paged[1]
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> uploader = new LinkedHashMap<String, Object>();
            uploader.put("userId", row.get("user_uuid"));
            uploader.put("nickname", row.get("nickname"));
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("versionNo", intObj(row.get("version_no")));
            item.put("size", longObj(row.get("size_bytes")) == null ? 0L : longObj(row.get("size_bytes")));
            item.put("sha256", row.get("sha256"));
            item.put("uploader", uploader);
            item.put("createdAt", dt(row.get("created_at")));
            item.put("changeSummary", row.get("change_summary"));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> initResourceVersionUpload(String userUuid, String resourceId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResourceDetail(user.getId(), resourceId);
        String filename = trim(str(request, "filename"));
        Long totalSize = longVal(request, "totalSize");
        Integer totalParts = intVal(request, "totalParts");
        String sha256 = trim(str(request, "sha256")).toLowerCase(Locale.ROOT);
        if (filename.isEmpty() || totalSize == null || totalSize.longValue() <= 0L || totalParts == null || totalParts.intValue() <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "filename/totalSize/totalParts必填");
        }
        String uploadId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO upload_sessions (upload_uuid, user_id, space_id, parent_resource_id, filename, total_size, total_parts, sha256, status, expires_at, created_at, updated_at, client_upload_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'initiated', DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW(), NOW(), ?)",
                uploadId, user.getId(), resource.get("space_id"), resource.get("parent_id"), filename, totalSize, totalParts,
                emptyToNull(sha256), "v2ver:" + resourceId + ":" + uploadId
        );
        try {
            Files.createDirectories(Paths.get("data", "local-storage", "uploads", uploadId));
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "创建上传目录失败");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("uploadId", uploadId);
        data.put("totalParts", totalParts);
        data.put("expiresAt", LocalDateTime.now().plusHours(24).format(FMT));
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> uploadResourceVersionPart(String userUuid, String resourceId, String uploadId, Integer partNumber, InputStream stream) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResourceDetail(user.getId(), resourceId);
        Map<String, Object> session = requireUploadSession(uploadId, user.getId());
        if (!longObj(session.get("space_id")).equals(longObj(resource.get("space_id")))) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "上传会话不属于该资源");
        }
        int pn = partNumber == null ? 0 : partNumber.intValue();
        int totalParts = intObj(session.get("total_parts")) == null ? 0 : intObj(session.get("total_parts"));
        if (pn < 1 || pn > totalParts) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "partNumber不合法");
        }
        Path partPath = Paths.get("data", "local-storage", "uploads", trim(uploadId), String.valueOf(pn));
        long size = writePartAndGetSize(stream, partPath);
        String etag = md5File(partPath);

        jdbcTemplate.update(
                "INSERT INTO upload_parts (upload_session_id, part_number, part_size, etag, checksum, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, NULL, 'uploaded', NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE part_size = VALUES(part_size), etag = VALUES(etag), status = 'uploaded', updated_at = NOW()",
                session.get("id"), pn, size, etag
        );
        jdbcTemplate.update("UPDATE upload_sessions SET status = 'uploading', updated_at = NOW() WHERE id = ?", session.get("id"));

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("partNumber", pn);
        data.put("etag", etag);
        data.put("size", size);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> completeResourceVersionUpload(String userUuid, String resourceId, String uploadId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResourceDetail(user.getId(), resourceId);
        Map<String, Object> session = requireUploadSession(uploadId, user.getId());
        Integer totalParts = intObj(session.get("total_parts"));
        Long uploadedParts = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM upload_parts WHERE upload_session_id = ?", Long.class, session.get("id"));
        if (uploadedParts == null || totalParts == null || uploadedParts.longValue() != totalParts.intValue()) {
            throw new BizException(ErrorCode.CONFLICT, 409, "分片未上传完整");
        }

        Path mergeFile = Paths.get("data", "local-storage", "uploads", trim(uploadId), "merged.bin");
        mergeParts(trim(uploadId), totalParts.intValue(), mergeFile);
        long size;
        String sha256;
        try {
            size = Files.size(mergeFile);
            sha256 = sha256File(mergeFile);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "读取合并文件失败");
        }
        Long expectSize = longObj(session.get("total_size"));
        if (expectSize != null && expectSize.longValue() != size) {
            throw new BizException(ErrorCode.CONFLICT, 409, "文件大小校验失败");
        }
        String expectSha = trim(str(session.get("sha256"))).toLowerCase(Locale.ROOT);
        if (!expectSha.isEmpty() && !expectSha.equals(sha256)) {
            throw new BizException(ErrorCode.CONFLICT, 409, "文件摘要校验失败");
        }

        Long objectId = findOrCreateStorageObject(mergeFile, sha256, size);
        Integer maxNo = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(version_no), 0) FROM resource_versions WHERE resource_id = ?", Integer.class, resource.get("id"));
        int newVersionNo = (maxNo == null ? 0 : maxNo.intValue()) + 1;
        String changeSummary = trim(str(request, "changeSummary"));
        jdbcTemplate.update(
                "INSERT INTO resource_versions (version_uuid, resource_id, version_no, object_id, size_bytes, sha256, uploader_user_id, change_summary, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                UUID.randomUUID().toString(), resource.get("id"), newVersionNo, objectId, size, sha256, user.getId(), emptyToNull(changeSummary)
        );
        jdbcTemplate.update(
                "UPDATE resources SET object_id = ?, size_bytes = ?, version_no = ?, updated_at = NOW() WHERE id = ?",
                objectId, size, newVersionNo, resource.get("id")
        );
        jdbcTemplate.update(
                "UPDATE upload_sessions SET status = 'completed', completed_at = NOW(), updated_at = NOW() WHERE id = ?",
                session.get("id")
        );
        cleanupUploadDir(trim(uploadId));

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("resourceId", resourceId);
        data.put("versionNo", newVersionNo);
        data.put("size", size);
        data.put("sha256", sha256);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> restoreResourceVersion(String userUuid, String resourceId, Integer versionNo) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResourceDetail(user.getId(), resourceId);
        if (versionNo == null || versionNo.intValue() <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "versionNo不合法");
        }
        Map<String, Object> version = requireResourceVersion(resource.get("id"), versionNo);
        jdbcTemplate.update(
                "UPDATE resources SET object_id = ?, size_bytes = ?, version_no = ?, updated_at = NOW() WHERE id = ?",
                version.get("object_id"), version.get("size_bytes"), versionNo, resource.get("id")
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("resourceId", resourceId);
        data.put("versionNo", versionNo);
        data.put("restored", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> getResourceVersionDownloadUrl(String userUuid, String resourceId, Integer versionNo, String requestBaseUrl) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResourceDetail(user.getId(), resourceId);
        ensureCurrentVersionSnapshot(resource, user.getId());
        Map<String, Object> version = requireResourceVersion(resource.get("id"), versionNo);
        Map<String, Object> object = jdbcTemplate.queryForObject(
                "SELECT object_key FROM storage_objects WHERE id = ? LIMIT 1",
                mapRowMapper(), version.get("object_id")
        );
        String base = normalizeBaseUrl(requestBaseUrl);
        long exp = Instant.now().getEpochSecond() + 3600L;
        String sig = FileAccessSigner.sign(appProperties.resolveFileAccessSecret(), resourceId, user.getUserUuid(), exp, "download");
        String url = base + "/api/v1/file-access?resourceId=" + urlEncode(resourceId)
                + "&userId=" + urlEncode(user.getUserUuid())
                + "&exp=" + exp
                + "&mode=download"
                + "&sig=" + urlEncode(sig);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("url", url);
        data.put("versionNo", versionNo);
        data.put("objectKey", object == null ? null : object.get("object_key"));
        data.put("expiresAt", Instant.ofEpochSecond(exp).toString());
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> createPreviewJob(String userUuid, String resourceId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> resource = requireOwnedResourceDetail(user.getId(), resourceId);
        String jobType = trim(str(request, "jobType")).toLowerCase(Locale.ROOT);
        if (!"thumbnail".equals(jobType) && !"pdf".equals(jobType) && !"video_transcode".equals(jobType)) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "jobType不合法");
        }
        String jobId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO preview_jobs (job_uuid, resource_id, object_id, job_type, status, retry_count, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'pending', 0, NOW(), NOW())",
                jobId, resource.get("id"), resource.get("object_id"), jobType
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("jobId", jobId);
        data.put("status", "pending");
        data.put("jobType", jobType);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPreviewJob(String userUuid, String jobId) {
        User user = requireUser(userUuid);
        Map<String, Object> job = requirePreviewJobOwnedByUser(user.getId(), jobId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("jobId", job.get("job_uuid"));
        data.put("resourceId", job.get("resource_uuid"));
        data.put("jobType", job.get("job_type"));
        data.put("status", job.get("status"));
        data.put("outputUrl", job.get("output_url"));
        data.put("errorMessage", job.get("error_message"));
        data.put("startedAt", dt(job.get("started_at")));
        data.put("finishedAt", dt(job.get("finished_at")));
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listPreviewJobs(String userUuid, String status, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        int[] paged = normalizePage(page, pageSize);
        int offset = (paged[0] - 1) * paged[1];
        String st = trim(status).toLowerCase(Locale.ROOT);
        if (!st.isEmpty() && !"pending".equals(st) && !"running".equals(st) && !"success".equals(st) && !"failed".equals(st)) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "status不合法");
        }
        String stSql = st.isEmpty() ? null : st;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM preview_jobs pj JOIN resources r ON r.id = pj.resource_id WHERE r.owner_user_id = ? AND (? IS NULL OR pj.status = ?)",
                Long.class, user.getId(), stSql, stSql
        );
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT pj.job_uuid, pj.job_type, pj.status, pj.error_message, pj.started_at, pj.finished_at, r.resource_uuid " +
                        "FROM preview_jobs pj JOIN resources r ON r.id = pj.resource_id " +
                        "WHERE r.owner_user_id = ? AND (? IS NULL OR pj.status = ?) " +
                        "ORDER BY pj.created_at DESC, pj.id DESC LIMIT ?, ?",
                mapRowMapper(), user.getId(), stSql, stSql, offset, paged[1]
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("jobId", row.get("job_uuid"));
            item.put("resourceId", row.get("resource_uuid"));
            item.put("jobType", row.get("job_type"));
            item.put("status", row.get("status"));
            item.put("errorMessage", row.get("error_message"));
            item.put("startedAt", dt(row.get("started_at")));
            item.put("finishedAt", dt(row.get("finished_at")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> updateSharePolicy(String userUuid, String shareId, Map<String, Object> request) {
        User user = requireUser(userUuid);
        Map<String, Object> share = requireOwnedShare(user.getId(), shareId);
        List<String> sets = new ArrayList<String>();
        List<Object> args = new ArrayList<Object>();

        if (request.containsKey("allowPreview")) {
            sets.add("allow_preview = ?");
            args.add(boolToTiny(request.get("allowPreview")));
        }
        if (request.containsKey("allowDownload")) {
            sets.add("allow_download = ?");
            args.add(boolToTiny(request.get("allowDownload")));
        }
        if (request.containsKey("needLogin")) {
            sets.add("need_login = ?");
            args.add(boolToTiny(request.get("needLogin")));
        }
        if (request.containsKey("allowSaveToMine")) {
            sets.add("allow_save_to_mine = ?");
            args.add(boolToTiny(request.get("allowSaveToMine")));
        }
        if (request.containsKey("maxAccessCount")) {
            sets.add("max_access_count = ?");
            args.add(intVal(request, "maxAccessCount"));
        }
        if (request.containsKey("expiredAt")) {
            sets.add("expired_at = ?");
            args.add(parseDateTimeOrNull(str(request, "expiredAt")));
        }
        if (request.containsKey("accessScope")) {
            sets.add("access_scope_json = ?");
            Object scope = request.get("accessScope");
            args.add(scope == null ? null : String.valueOf(scope));
        }
        if (sets.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "至少提供一个可更新字段");
        }
        sets.add("updated_at = NOW()");
        args.add(share.get("id"));
        jdbcTemplate.update("UPDATE shares SET " + joinClauses(sets) + " WHERE id = ?", args.toArray());
        Map<String, Object> latest = requireOwnedShare(user.getId(), shareId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", latest.get("share_uuid"));
        data.put("allowPreview", tinyToBool(latest.get("allow_preview")));
        data.put("allowDownload", tinyToBool(latest.get("allow_download")));
        data.put("needLogin", tinyToBool(latest.get("need_login")));
        data.put("allowSaveToMine", tinyToBool(latest.get("allow_save_to_mine")));
        data.put("maxAccessCount", latest.get("max_access_count"));
        data.put("expiredAt", dt(latest.get("expired_at")));
        data.put("accessScope", latest.get("access_scope_json"));
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listShareAccessLogs(String userUuid, String shareId, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        Map<String, Object> share = requireOwnedShare(user.getId(), shareId);
        int[] paged = normalizePage(page, pageSize);
        int offset = (paged[0] - 1) * paged[1];
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM share_access_logs WHERE share_id = ?", Long.class, share.get("id"));
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT sal.id, sal.ip, sal.user_agent, sal.request_id, sal.verify_passed, sal.accessed_at, u.user_uuid AS visitor_user_uuid " +
                        "FROM share_access_logs sal " +
                        "LEFT JOIN users u ON u.id = sal.visitor_user_id " +
                        "WHERE sal.share_id = ? ORDER BY sal.accessed_at DESC, sal.id DESC LIMIT ?, ?",
                mapRowMapper(), share.get("id"), offset, paged[1]
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", row.get("id"));
            item.put("visitorUserId", row.get("visitor_user_uuid"));
            item.put("ip", row.get("ip"));
            item.put("userAgent", row.get("user_agent"));
            item.put("requestId", row.get("request_id"));
            item.put("verifyPassed", tinyToBool(row.get("verify_passed")));
            item.put("accessedAt", dt(row.get("accessed_at")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getShareStats(String userUuid, String shareId) {
        User user = requireUser(userUuid);
        Map<String, Object> share = requireOwnedShare(user.getId(), shareId);
        Map<String, Object> stats = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) AS visit_total, " +
                        "SUM(CASE WHEN action = 'download' THEN 1 ELSE 0 END) AS download_total, " +
                        "COUNT(DISTINCT CONCAT(IFNULL(visitor_user_id,0), '#', IFNULL(ip,''))) AS unique_visitors, " +
                        "MAX(accessed_at) AS last_access_at " +
                        "FROM share_access_logs WHERE share_id = ?",
                mapRowMapper(), share.get("id")
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("visitTotal", longObj(stats.get("visit_total")) == null ? 0L : longObj(stats.get("visit_total")));
        data.put("downloadTotal", longObj(stats.get("download_total")) == null ? 0L : longObj(stats.get("download_total")));
        data.put("uniqueVisitors", longObj(stats.get("unique_visitors")) == null ? 0L : longObj(stats.get("unique_visitors")));
        data.put("lastAccessAt", dt(stats.get("last_access_at")));
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listNotifications(String userUuid, Boolean unreadOnly, Integer page, Integer pageSize) {
        User user = requireUser(userUuid);
        int[] paged = normalizePage(page, pageSize);
        int offset = (paged[0] - 1) * paged[1];
        boolean onlyUnread = unreadOnly != null && unreadOnly.booleanValue();

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM notifications WHERE user_id = ? AND (? = 0 OR is_read = 0)",
                Long.class, user.getId(), onlyUnread ? 1 : 0
        );
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT notification_uuid, type, title, content, payload_json, is_read, read_at, created_at " +
                        "FROM notifications WHERE user_id = ? AND (? = 0 OR is_read = 0) " +
                        "ORDER BY created_at DESC, id DESC LIMIT ?, ?",
                mapRowMapper(), user.getId(), onlyUnread ? 1 : 0, offset, paged[1]
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", row.get("notification_uuid"));
            item.put("type", row.get("type"));
            item.put("title", row.get("title"));
            item.put("content", row.get("content"));
            item.put("payload", row.get("payload_json"));
            item.put("isRead", tinyToBool(row.get("is_read")));
            item.put("readAt", dt(row.get("read_at")));
            item.put("createdAt", dt(row.get("created_at")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> readNotification(String userUuid, String notificationId) {
        User user = requireUser(userUuid);
        Map<String, Object> notification = requireOwnedNotification(user.getId(), notificationId);
        jdbcTemplate.update("UPDATE notifications SET is_read = 1, read_at = NOW() WHERE id = ?", notification.get("id"));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", notificationId);
        data.put("read", true);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> readAllNotifications(String userUuid) {
        User user = requireUser(userUuid);
        int changed = jdbcTemplate.update("UPDATE notifications SET is_read = 1, read_at = NOW() WHERE user_id = ? AND is_read = 0", user.getId());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("updated", changed);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> deleteNotification(String userUuid, String notificationId) {
        User user = requireUser(userUuid);
        Map<String, Object> notification = requireOwnedNotification(user.getId(), notificationId);
        jdbcTemplate.update("DELETE FROM notifications WHERE id = ?", notification.get("id"));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", notificationId);
        data.put("deleted", true);
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPublicGroup(String groupId) {
        Map<String, Object> group = requirePublicGroup(groupId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", group.get("space_uuid"));
        data.put("name", group.get("name"));
        data.put("description", group.get("description"));
        data.put("visibility", group.get("visibility"));
        data.put("memberCount", countMembers(longObj(group.get("id"))));
        data.put("usedBytes", longObj(group.get("used_bytes")) == null ? 0L : longObj(group.get("used_bytes")));
        data.put("quotaBytes", longObj(group.get("quota_bytes")) == null ? 0L : longObj(group.get("quota_bytes")));
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listPublicGroupResources(String groupId, Integer page, Integer pageSize) {
        Map<String, Object> group = requirePublicGroup(groupId);
        int[] paged = normalizePage(page, pageSize);
        int p = paged[0];
        int size = paged[1];
        int offset = (p - 1) * size;
        Long gid = longObj(group.get("id"));
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM resources WHERE space_id = ? AND is_deleted = 0",
                Long.class, gid
        );
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT resource_uuid, parent_id, resource_type, name, extension, size_bytes, created_at, updated_at " +
                        "FROM resources WHERE space_id = ? AND is_deleted = 0 " +
                        "ORDER BY CASE WHEN resource_type='folder' THEN 0 ELSE 1 END ASC, name_normalized ASC, id ASC " +
                        "LIMIT ?, ?",
                mapRowMapper(), gid, offset, size
        );
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", str(row.get("resource_uuid")));
            item.put("parentId", row.get("parent_id"));
            item.put("type", str(row.get("resource_type")));
            item.put("name", str(row.get("name")));
            item.put("extension", str(row.get("extension")));
            item.put("sizeBytes", longObj(row.get("size_bytes")) == null ? 0L : longObj(row.get("size_bytes")));
            item.put("createdAt", dt(row.get("created_at")));
            item.put("updatedAt", dt(row.get("updated_at")));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    private Map<String, Object> requireOwnedResourceDetail(Long userId, String resourceUuid) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, resource_uuid, space_id, parent_id, object_id, size_bytes, version_no, name " +
                        "FROM resources WHERE resource_uuid = ? AND owner_user_id = ? AND is_deleted = 0 LIMIT 1",
                mapRowMapper(), trim(resourceUuid), userId
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "资源不存在");
        }
        return rows.get(0);
    }

    private void ensureCurrentVersionSnapshot(Map<String, Object> resource, Long userId) {
        Integer currentVersionNo = intObj(resource.get("version_no"));
        if (currentVersionNo == null || currentVersionNo.intValue() <= 0) {
            currentVersionNo = Integer.valueOf(1);
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM resource_versions WHERE resource_id = ? AND version_no = ?",
                Integer.class, resource.get("id"), currentVersionNo
        );
        if (exists != null && exists.intValue() > 0) {
            return;
        }
        Long objectId = longObj(resource.get("object_id"));
        if (objectId == null) {
            return;
        }
        Map<String, Object> object = jdbcTemplate.queryForObject(
                "SELECT sha256, size_bytes FROM storage_objects WHERE id = ? LIMIT 1",
                mapRowMapper(), objectId
        );
        jdbcTemplate.update(
                "INSERT INTO resource_versions (version_uuid, resource_id, version_no, object_id, size_bytes, sha256, uploader_user_id, change_summary, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                UUID.randomUUID().toString(),
                resource.get("id"),
                currentVersionNo,
                objectId,
                resource.get("size_bytes"),
                object == null ? null : object.get("sha256"),
                userId,
                "initial snapshot"
        );
    }

    private Map<String, Object> requireUploadSession(String uploadId, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, user_id, space_id, total_parts, total_size, sha256, status FROM upload_sessions WHERE upload_uuid = ? LIMIT 1",
                mapRowMapper(), trim(uploadId)
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "上传会话不存在");
        }
        Map<String, Object> session = rows.get(0);
        if (!userId.equals(longObj(session.get("user_id")))) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "无权操作该上传会话");
        }
        String status = trim(str(session.get("status")));
        if ("completed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
            throw new BizException(ErrorCode.CONFLICT, 409, "上传会话不可写");
        }
        return session;
    }

    private long writePartAndGetSize(InputStream stream, Path partPath) {
        if (stream == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "分片内容为空");
        }
        try {
            Files.createDirectories(partPath.getParent());
            byte[] buf = new byte[8192];
            long total = 0L;
            try (InputStream in = stream; java.io.OutputStream out = Files.newOutputStream(partPath)) {
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n > 0) {
                        out.write(buf, 0, n);
                        total += n;
                    }
                }
            }
            if (total <= 0L) {
                throw new BizException(ErrorCode.INVALID_PARAM, 400, "分片内容为空");
            }
            return total;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "写入分片失败");
        }
    }

    private String md5File(Path file) {
        return digestFile(file, "MD5");
    }

    private String sha256File(Path file) {
        return digestFile(file, "SHA-256");
    }

    private String digestFile(Path file, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buf = new byte[8192];
            try (InputStream in = Files.newInputStream(file)) {
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (n > 0) {
                        digest.update(buf, 0, n);
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "计算摘要失败");
        }
    }

    private void mergeParts(String uploadId, int totalParts, Path mergedFile) {
        try {
            Files.createDirectories(mergedFile.getParent());
            try (java.io.OutputStream out = Files.newOutputStream(mergedFile)) {
                byte[] buf = new byte[8192];
                for (int i = 1; i <= totalParts; i++) {
                    Path part = Paths.get("data", "local-storage", "uploads", uploadId, String.valueOf(i));
                    if (!Files.exists(part)) {
                        throw new BizException(ErrorCode.CONFLICT, 409, "分片文件缺失");
                    }
                    try (InputStream in = Files.newInputStream(part)) {
                        int n;
                        while ((n = in.read(buf)) >= 0) {
                            if (n > 0) {
                                out.write(buf, 0, n);
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "合并分片失败");
        }
    }

    private Long findOrCreateStorageObject(Path mergedFile, String sha256, long size) {
        Long existingId = jdbcTemplate.queryForObject(
                "SELECT id FROM storage_objects WHERE sha256 = ? AND size_bytes = ? LIMIT 1",
                Long.class, sha256, size
        );
        if (existingId != null) {
            return existingId;
        }
        Path objectPath = Paths.get("data", "local-storage", "objects", sha256.substring(0, 2), sha256);
        try {
            Files.createDirectories(objectPath.getParent());
            if (!Files.exists(objectPath)) {
                Files.move(mergedFile, objectPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "保存对象文件失败");
        }
        String objectUuid = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO storage_objects (object_uuid, sha256, md5, size_bytes, mime_type, storage_provider, bucket_name, object_key, storage_class, created_at, updated_at) " +
                        "VALUES (?, ?, NULL, ?, NULL, 'local', 'local', ?, 'standard', NOW(), NOW())",
                objectUuid, sha256, size, objectPath.toString().replace("\\", "/")
        );
        return jdbcTemplate.queryForObject("SELECT id FROM storage_objects WHERE object_uuid = ? LIMIT 1", Long.class, objectUuid);
    }

    private void cleanupUploadDir(String uploadId) {
        Path dir = Paths.get("data", "local-storage", "uploads", uploadId);
        if (!Files.exists(dir)) {
            return;
        }
        try {
            List<Path> files = new ArrayList<Path>();
            java.util.stream.Stream<Path> walk = Files.walk(dir);
            try {
                walk.forEach(files::add);
            } finally {
                walk.close();
            }
            for (int i = files.size() - 1; i >= 0; i--) {
                Files.deleteIfExists(files.get(i));
            }
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> requireResourceVersion(Object resourceId, Integer versionNo) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, version_no, object_id, size_bytes, sha256 FROM resource_versions WHERE resource_id = ? AND version_no = ? LIMIT 1",
                mapRowMapper(), resourceId, versionNo
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "版本不存在");
        }
        return rows.get(0);
    }

    private String normalizeBaseUrl(String requestBaseUrl) {
        String base = trim(requestBaseUrl);
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception ex) {
            return "";
        }
    }

    private Map<String, Object> requirePreviewJobOwnedByUser(Long userId, String jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT pj.id, pj.job_uuid, pj.job_type, pj.status, pj.error_message, pj.started_at, pj.finished_at, " +
                        "r.resource_uuid, so.object_key, CASE WHEN so.id IS NULL THEN NULL ELSE CONCAT('/local/', so.id) END AS output_url " +
                        "FROM preview_jobs pj " +
                        "JOIN resources r ON r.id = pj.resource_id " +
                        "LEFT JOIN storage_objects so ON so.id = pj.output_object_id " +
                        "WHERE pj.job_uuid = ? AND r.owner_user_id = ? LIMIT 1",
                mapRowMapper(), trim(jobId), userId
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "任务不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireOwnedShare(Long userId, String shareId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, share_uuid, allow_preview, allow_download, need_login, allow_save_to_mine, max_access_count, expired_at, access_scope_json " +
                        "FROM shares WHERE share_uuid = ? AND creator_user_id = ? LIMIT 1",
                mapRowMapper(), trim(shareId), userId
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "分享不存在");
        }
        return rows.get(0);
    }

    private Integer boolToTiny(Object val) {
        if (val == null) {
            return Integer.valueOf(0);
        }
        if (val instanceof Boolean) {
            return ((Boolean) val).booleanValue() ? Integer.valueOf(1) : Integer.valueOf(0);
        }
        String s = trim(String.valueOf(val)).toLowerCase(Locale.ROOT);
        if ("1".equals(s) || "true".equals(s) || "yes".equals(s)) {
            return Integer.valueOf(1);
        }
        return Integer.valueOf(0);
    }

    private Boolean tinyToBool(Object val) {
        Integer i = intObj(val);
        return i != null && i.intValue() == 1;
    }

    private LocalDateTime parseDateTimeOrNull(String val) {
        String t = trim(val);
        if (t.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(t);
        } catch (DateTimeParseException ex) {
            try {
                return OffsetDateTime.parse(t).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                throw new BizException(ErrorCode.INVALID_PARAM, 400, "时间格式不合法");
            }
        }
    }

    private Map<String, Object> requireOwnedNotification(Long userId, String notificationId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id FROM notifications WHERE notification_uuid = ? AND user_id = ? LIMIT 1",
                mapRowMapper(), trim(notificationId), userId
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "通知不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireGroup(String groupId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, space_uuid, owner_user_id, name, description, visibility, avatar_url, quota_bytes, used_bytes, max_members, created_at, is_archived, status " +
                        "FROM spaces WHERE space_uuid = ? AND space_type = 'team'",
                mapRowMapper(), trim(groupId)
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "群组不存在");
        }
        Map<String, Object> row = rows.get(0);
        if (intObj(row.get("status")) != null && intObj(row.get("status")) != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "群组不存在");
        }
        return row;
    }

    private Map<String, Object> requirePublicGroup(String groupId) {
        Map<String, Object> group = requireGroup(groupId);
        if (!"public".equalsIgnoreCase(str(group.get("visibility")))) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "该群组非公开");
        }
        if (intObj(group.get("is_archived")) != null && intObj(group.get("is_archived")) == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "群组已归档");
        }
        return group;
    }

    private Map<String, Object> requireGroupAndMembership(String groupId, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT s.id, s.space_uuid, s.owner_user_id, s.name, s.description, s.visibility, s.avatar_url, s.quota_bytes, s.used_bytes, s.max_members, s.created_at, s.is_archived, s.status, sm.role_code " +
                        "FROM spaces s " +
                        "JOIN space_members sm ON sm.space_id = s.id AND sm.user_id = ? AND sm.status = 1 " +
                        "WHERE s.space_uuid = ? AND s.space_type = 'team' LIMIT 1",
                mapRowMapper(), userId, trim(groupId)
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "群组不存在或无权访问");
        }
        Map<String, Object> group = rows.get(0);
        if (intObj(group.get("status")) != null && intObj(group.get("status")) != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "群组不存在");
        }
        return group;
    }

    private Map<String, Object> buildGroupDetail(Map<String, Object> group) {
        Map<String, Object> owner = jdbcTemplate.queryForObject(
                "SELECT user_uuid, nickname, email, avatar_url FROM users WHERE id = ? LIMIT 1",
                mapRowMapper(), group.get("owner_user_id")
        );
        Map<String, Object> ownerObj = new LinkedHashMap<String, Object>();
        ownerObj.put("userId", owner == null ? null : owner.get("user_uuid"));
        ownerObj.put("nickname", owner == null ? null : owner.get("nickname"));
        ownerObj.put("email", owner == null ? null : owner.get("email"));
        ownerObj.put("avatarUrl", owner == null ? null : owner.get("avatar_url"));

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", group.get("space_uuid"));
        data.put("name", group.get("name"));
        data.put("description", group.get("description"));
        data.put("visibility", group.get("visibility"));
        data.put("owner", ownerObj);
        data.put("memberCount", countMembers(longObj(group.get("id"))));
        data.put("usedBytes", longObj(group.get("used_bytes")) == null ? 0L : longObj(group.get("used_bytes")));
        data.put("quotaBytes", longObj(group.get("quota_bytes")) == null ? 0L : longObj(group.get("quota_bytes")));
        data.put("maxMembers", intObj(group.get("max_members")) == null ? 0 : intObj(group.get("max_members")));
        data.put("avatarUrl", group.get("avatar_url"));
        data.put("createdAt", dt(group.get("created_at")));
        data.put("role", group.get("role_code"));
        data.put("archived", intObj(group.get("is_archived")) != null && intObj(group.get("is_archived")) == 1);
        return data;
    }

    private Long countMembers(Long spaceId) {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM space_members WHERE space_id = ? AND status = 1", Long.class, spaceId);
        return c == null ? 0L : c.longValue();
    }

    private Map<String, Object> requirePendingInvite(String inviteToken) {
        String tokenHash = sha256Hex(trim(inviteToken));
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, invite_uuid, space_id, inviter_user_id, invitee_user_id, invitee_email, role_code, status, expired_at " +
                        "FROM space_invites WHERE token_hash = ? LIMIT 1",
                mapRowMapper(), tokenHash
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "邀请不存在");
        }
        Map<String, Object> invite = rows.get(0);
        if (!"pending".equalsIgnoreCase(str(invite.get("status")))) {
            throw new BizException(ErrorCode.CONFLICT, 409, "邀请已处理");
        }
        String expiredAt = dt(invite.get("expired_at"));
        if (!expiredAt.isEmpty() && expiredAt.compareTo(now()) < 0) {
            jdbcTemplate.update("UPDATE space_invites SET status = 'expired', updated_at = NOW() WHERE id = ?", invite.get("id"));
            throw new BizException(ErrorCode.CONFLICT, 409, "邀请已过期");
        }
        return invite;
    }

    private Map<String, Object> requireJoinRequest(Object spaceId, String requestId) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, applicant_user_id, status FROM space_join_requests WHERE request_uuid = ? AND space_id = ? LIMIT 1",
                mapRowMapper(), trim(requestId), spaceId
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "申请不存在");
        }
        Map<String, Object> req = rows.get(0);
        if (!"pending".equalsIgnoreCase(str(req.get("status")))) {
            throw new BizException(ErrorCode.CONFLICT, 409, "申请已处理");
        }
        return req;
    }

    private Map<String, Object> requireOwnedResource(Long userId, String resourceUuid) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, resource_uuid FROM resources WHERE resource_uuid = ? AND owner_user_id = ? AND is_deleted = 0 LIMIT 1",
                mapRowMapper(), trim(resourceUuid), userId
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "资源不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireOwnedTag(Long userId, String tagUuid) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, tag_uuid, name, color FROM tags WHERE tag_uuid = ? AND owner_user_id = ? LIMIT 1",
                mapRowMapper(), trim(tagUuid), userId
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, 404, "标签不存在");
        }
        return rows.get(0);
    }

    private void requireOwnerOrEditor(Map<String, Object> group) {
        String role = str(group.get("role_code")).toLowerCase(Locale.ROOT);
        if (!"owner".equals(role) && !"editor".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "权限不足");
        }
    }

    private void requireOwner(Map<String, Object> group) {
        String role = str(group.get("role_code")).toLowerCase(Locale.ROOT);
        if (!"owner".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, 403, "仅群主可操作");
        }
    }

    private User requireUser(String userUuid) {
        String id = trim(userUuid);
        if (id.isEmpty() || "null".equalsIgnoreCase(id)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        User user = userMapper.findByUserUuid(id);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, 401, "未授权");
        }
        return user;
    }

    private String normalizeVisibility(String visibility) {
        String val = trim(visibility).toLowerCase(Locale.ROOT);
        if (val.isEmpty()) {
            return "private";
        }
        if (!"private".equals(val) && !"public".equals(val)) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "visibility仅支持private/public");
        }
        return val;
    }

    private String normalizeRole(String role, boolean allowOwner) {
        String val = trim(role).toLowerCase(Locale.ROOT);
        if (val.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "role必填");
        }
        if ("owner".equals(val) && allowOwner) {
            return val;
        }
        if (!"editor".equals(val) && !"viewer".equals(val)) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "role仅支持editor/viewer" + (allowOwner ? "/owner" : ""));
        }
        return val;
    }

    private int[] normalizePage(Integer page, Integer pageSize) {
        int p = page == null ? 1 : page.intValue();
        int size = pageSize == null ? 20 : pageSize.intValue();
        if (p < 1 || size < 1 || size > 200) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, "分页参数不合法");
        }
        return new int[]{p, size};
    }

    private String normalizeLikeKeyword(String keyword) {
        String kw = trim(keyword);
        if (kw.isEmpty()) {
            return null;
        }
        return "%" + kw + "%";
    }

    private String joinClauses(List<String> clauses) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clauses.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(clauses.get(i));
        }
        return sb.toString();
    }

    private String now() {
        return LocalDateTime.now().format(FMT);
    }

    private String sha256Hex(String plain) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(trim(plain).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL, 500, "系统错误");
        }
    }

    private RowMapper<Map<String, Object>> mapRowMapper() {
        return (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            int columns = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= columns; i++) {
                row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
            }
            return row;
        };
    }

    private String dt(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(FMT);
        }
        return String.valueOf(value);
    }

    private String str(Map<String, Object> src, String key) {
        if (src == null) {
            return "";
        }
        return str(src.get(key));
    }

    private String str(Object val) {
        return val == null ? "" : String.valueOf(val);
    }

    private String trim(String val) {
        return val == null ? "" : val.trim();
    }

    private String emptyToNull(String val) {
        String t = trim(val);
        return t.isEmpty() ? null : t;
    }

    private Long longVal(Map<String, Object> src, String key) {
        if (src == null || !src.containsKey(key) || src.get(key) == null) {
            return null;
        }
        Object v = src.get(key);
        if (v instanceof Number) {
            return Long.valueOf(((Number) v).longValue());
        }
        String s = trim(String.valueOf(v));
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(Long.parseLong(s));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, key + "必须是数字");
        }
    }

    private Integer intVal(Map<String, Object> src, String key) {
        if (src == null || !src.containsKey(key) || src.get(key) == null) {
            return null;
        }
        Object v = src.get(key);
        if (v instanceof Number) {
            return Integer.valueOf(((Number) v).intValue());
        }
        String s = trim(String.valueOf(v));
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(s));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, 400, key + "必须是整数");
        }
    }

    private Long longObj(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        try {
            return Long.valueOf(Long.parseLong(String.valueOf(value)));
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer intObj(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        try {
            return Integer.valueOf(Integer.parseInt(String.valueOf(value)));
        } catch (Exception ex) {
            return null;
        }
    }
}
