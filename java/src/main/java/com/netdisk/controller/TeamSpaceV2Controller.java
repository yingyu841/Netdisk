package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.service.TeamSpaceV2Service;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
public class TeamSpaceV2Controller {
    private final TeamSpaceV2Service teamSpaceV2Service;

    public TeamSpaceV2Controller(TeamSpaceV2Service teamSpaceV2Service) {
        this.teamSpaceV2Service = teamSpaceV2Service;
    }

    @PostMapping("/groups")
    public ApiResponse<Map<String, Object>> createGroup(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.createGroup(currentUser(req), safeBody(request)), requestId(req));
    }

    @GetMapping("/groups")
    public ApiResponse<Map<String, Object>> listGroups(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listMyGroups(currentUser(req), keyword, page, pageSize), requestId(req));
    }

    @GetMapping("/groups/{groupId}")
    public ApiResponse<Map<String, Object>> groupDetail(@PathVariable String groupId, HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.getGroupDetail(currentUser(req), groupId), requestId(req));
    }

    @PatchMapping("/groups/{groupId}")
    public ApiResponse<Map<String, Object>> updateGroup(
            @PathVariable String groupId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.updateGroup(currentUser(req), groupId, safeBody(request)), requestId(req));
    }

    @PostMapping("/groups/{groupId}/archive")
    public ApiResponse<Map<String, Object>> archiveGroup(@PathVariable String groupId, HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.archiveGroup(currentUser(req), groupId), requestId(req));
    }

    @DeleteMapping("/groups/{groupId}")
    public ApiResponse<Map<String, Object>> deleteGroup(@PathVariable String groupId, HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.deleteGroup(currentUser(req), groupId), requestId(req));
    }

    @GetMapping("/groups/{groupId}/members")
    public ApiResponse<Map<String, Object>> listMembers(
            @PathVariable String groupId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listMembers(currentUser(req), groupId, keyword, page, pageSize), requestId(req));
    }

    @PostMapping("/groups/{groupId}/invites")
    public ApiResponse<Map<String, Object>> inviteMember(
            @PathVariable String groupId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.inviteMember(currentUser(req), groupId, safeBody(request)), requestId(req));
    }

    @GetMapping("/groups/{groupId}/invites")
    public ApiResponse<Map<String, Object>> listInvites(
            @PathVariable String groupId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listInvites(currentUser(req), groupId, status, page, pageSize), requestId(req));
    }

    @PostMapping("/groups/{groupId}/invites/{inviteId}/cancel")
    public ApiResponse<Map<String, Object>> cancelInvite(
            @PathVariable String groupId,
            @PathVariable String inviteId,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.cancelInvite(currentUser(req), groupId, inviteId), requestId(req));
    }

    @PostMapping("/group-invites/{inviteToken}/accept")
    public ApiResponse<Map<String, Object>> acceptInvite(
            @PathVariable String inviteToken,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.acceptInvite(currentUser(req), inviteToken), requestId(req));
    }

    @PostMapping("/group-invites/{inviteToken}/reject")
    public ApiResponse<Map<String, Object>> rejectInvite(
            @PathVariable String inviteToken,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.rejectInvite(currentUser(req), inviteToken), requestId(req));
    }

    @PatchMapping("/groups/{groupId}/members/{userId}")
    public ApiResponse<Map<String, Object>> updateMemberRole(
            @PathVariable String groupId,
            @PathVariable String userId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.updateMemberRole(currentUser(req), groupId, userId, safeBody(request)), requestId(req));
    }

    @DeleteMapping("/groups/{groupId}/members/{userId}")
    public ApiResponse<Map<String, Object>> removeMember(
            @PathVariable String groupId,
            @PathVariable String userId,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.removeMember(currentUser(req), groupId, userId), requestId(req));
    }

    @PostMapping("/groups/{groupId}/join-requests")
    public ApiResponse<Map<String, Object>> createJoinRequest(
            @PathVariable String groupId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.createJoinRequest(currentUser(req), groupId, safeBody(request)), requestId(req));
    }

    @GetMapping("/me/group-join-requests")
    public ApiResponse<Map<String, Object>> myJoinRequests(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listMyJoinRequests(currentUser(req), page, pageSize), requestId(req));
    }

    @GetMapping("/groups/{groupId}/join-requests")
    public ApiResponse<Map<String, Object>> listJoinRequests(
            @PathVariable String groupId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listGroupJoinRequests(currentUser(req), groupId, status, page, pageSize), requestId(req));
    }

    @PostMapping("/groups/{groupId}/join-requests/{requestId}/approve")
    public ApiResponse<Map<String, Object>> approveJoinRequest(
            @PathVariable String groupId,
            @PathVariable String requestId,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.approveJoinRequest(currentUser(req), groupId, requestId), requestId(req));
    }

    @PostMapping("/groups/{groupId}/join-requests/{requestId}/reject")
    public ApiResponse<Map<String, Object>> rejectJoinRequest(
            @PathVariable String groupId,
            @PathVariable String requestId,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.rejectJoinRequest(currentUser(req), groupId, requestId), requestId(req));
    }

    @PostMapping("/resources/{resourceId}/favorite")
    public ApiResponse<Map<String, Object>> favoriteResource(@PathVariable String resourceId, HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.favoriteResource(currentUser(req), resourceId), requestId(req));
    }

    @DeleteMapping("/resources/{resourceId}/favorite")
    public ApiResponse<Map<String, Object>> unfavoriteResource(@PathVariable String resourceId, HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.unfavoriteResource(currentUser(req), resourceId), requestId(req));
    }

    @GetMapping("/me/favorites")
    public ApiResponse<Map<String, Object>> listFavorites(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listMyFavorites(currentUser(req), page, pageSize), requestId(req));
    }

    @PostMapping("/tags")
    public ApiResponse<Map<String, Object>> createTag(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.createTag(currentUser(req), safeBody(request)), requestId(req));
    }

    @GetMapping("/tags")
    public ApiResponse<Map<String, Object>> listTags(HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listTags(currentUser(req)), requestId(req));
    }

    @PatchMapping("/tags/{tagId}")
    public ApiResponse<Map<String, Object>> updateTag(
            @PathVariable String tagId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.updateTag(currentUser(req), tagId, safeBody(request)), requestId(req));
    }

    @DeleteMapping("/tags/{tagId}")
    public ApiResponse<Map<String, Object>> deleteTag(@PathVariable String tagId, HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.deleteTag(currentUser(req), tagId), requestId(req));
    }

    @PostMapping("/resources/{resourceId}/tags/{tagId}")
    public ApiResponse<Map<String, Object>> addTagToResource(
            @PathVariable String resourceId,
            @PathVariable String tagId,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.addTagToResource(currentUser(req), resourceId, tagId), requestId(req));
    }

    @DeleteMapping("/resources/{resourceId}/tags/{tagId}")
    public ApiResponse<Map<String, Object>> removeTagFromResource(
            @PathVariable String resourceId,
            @PathVariable String tagId,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.removeTagFromResource(currentUser(req), resourceId, tagId), requestId(req));
    }

    @GetMapping("/resources/{resourceId}/tags")
    public ApiResponse<Map<String, Object>> listResourceTags(
            @PathVariable String resourceId,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listResourceTags(currentUser(req), resourceId), requestId(req));
    }

    @GetMapping("/resources/{resourceId}/versions")
    public ApiResponse<Map<String, Object>> listResourceVersions(
            @PathVariable String resourceId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listResourceVersions(currentUser(req), resourceId, page, pageSize), requestId(req));
    }

    @PostMapping("/resources/{resourceId}/versions/init")
    public ApiResponse<Map<String, Object>> initResourceVersionUpload(
            @PathVariable String resourceId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.initResourceVersionUpload(currentUser(req), resourceId, safeBody(request)), requestId(req));
    }

    @PutMapping("/resources/{resourceId}/versions/{uploadId}/parts/{partNumber}")
    public ApiResponse<Map<String, Object>> uploadResourceVersionPart(
            @PathVariable String resourceId,
            @PathVariable String uploadId,
            @PathVariable Integer partNumber,
            HttpServletRequest req) throws IOException {
        return ApiResponse.ok(
                teamSpaceV2Service.uploadResourceVersionPart(currentUser(req), resourceId, uploadId, partNumber, req.getInputStream()),
                requestId(req)
        );
    }

    @PostMapping("/resources/{resourceId}/versions/{uploadId}/complete")
    public ApiResponse<Map<String, Object>> completeResourceVersionUpload(
            @PathVariable String resourceId,
            @PathVariable String uploadId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest req) {
        return ApiResponse.ok(
                teamSpaceV2Service.completeResourceVersionUpload(currentUser(req), resourceId, uploadId, safeBody(request)),
                requestId(req)
        );
    }

    @PostMapping("/resources/{resourceId}/versions/{versionNo}/restore")
    public ApiResponse<Map<String, Object>> restoreResourceVersion(
            @PathVariable String resourceId,
            @PathVariable Integer versionNo,
            HttpServletRequest req) {
        return ApiResponse.ok(
                teamSpaceV2Service.restoreResourceVersion(currentUser(req), resourceId, versionNo),
                requestId(req)
        );
    }

    @GetMapping("/resources/{resourceId}/versions/{versionNo}/download-url")
    public ApiResponse<Map<String, Object>> getResourceVersionDownloadUrl(
            @PathVariable String resourceId,
            @PathVariable Integer versionNo,
            HttpServletRequest req) {
        return ApiResponse.ok(
                teamSpaceV2Service.getResourceVersionDownloadUrl(currentUser(req), resourceId, versionNo, requestBaseUrl(req)),
                requestId(req)
        );
    }

    @PostMapping("/resources/{resourceId}/preview-jobs")
    public ApiResponse<Map<String, Object>> createPreviewJob(
            @PathVariable String resourceId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.createPreviewJob(currentUser(req), resourceId, safeBody(request)), requestId(req));
    }

    @GetMapping("/preview-jobs/{jobId}")
    public ApiResponse<Map<String, Object>> getPreviewJob(@PathVariable String jobId, HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.getPreviewJob(currentUser(req), jobId), requestId(req));
    }

    @GetMapping("/preview-jobs")
    public ApiResponse<Map<String, Object>> listPreviewJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listPreviewJobs(currentUser(req), status, page, pageSize), requestId(req));
    }

    @PatchMapping("/shares/{shareId}")
    public ApiResponse<Map<String, Object>> updateSharePolicy(
            @PathVariable String shareId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.updateSharePolicy(currentUser(req), shareId, safeBody(request)), requestId(req));
    }

    @GetMapping("/shares/{shareId}/access-logs")
    public ApiResponse<Map<String, Object>> listShareAccessLogs(
            @PathVariable String shareId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listShareAccessLogs(currentUser(req), shareId, page, pageSize), requestId(req));
    }

    @GetMapping("/shares/{shareId}/stats")
    public ApiResponse<Map<String, Object>> getShareStats(@PathVariable String shareId, HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.getShareStats(currentUser(req), shareId), requestId(req));
    }

    @GetMapping("/notifications")
    public ApiResponse<Map<String, Object>> listNotifications(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listNotifications(currentUser(req), unreadOnly, page, pageSize), requestId(req));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ApiResponse<Map<String, Object>> readNotification(@PathVariable String notificationId, HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.readNotification(currentUser(req), notificationId), requestId(req));
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse<Map<String, Object>> readAllNotifications(HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.readAllNotifications(currentUser(req)), requestId(req));
    }

    @DeleteMapping("/notifications/{notificationId}")
    public ApiResponse<Map<String, Object>> deleteNotification(@PathVariable String notificationId, HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.deleteNotification(currentUser(req), notificationId), requestId(req));
    }

    @GetMapping("/public/groups/{groupId}")
    public ApiResponse<Map<String, Object>> getPublicGroup(@PathVariable String groupId, HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.getPublicGroup(groupId), requestId(req));
    }

    @GetMapping("/public/groups/{groupId}/resources")
    public ApiResponse<Map<String, Object>> listPublicGroupResources(
            @PathVariable String groupId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(teamSpaceV2Service.listPublicGroupResources(groupId, page, pageSize), requestId(req));
    }

    private Map<String, Object> safeBody(Map<String, Object> request) {
        return request == null ? new LinkedHashMap<String, Object>() : request;
    }

    private String currentUser(HttpServletRequest req) {
        Object uid = req.getAttribute("authUserId");
        return uid == null ? "" : String.valueOf(uid);
    }

    private String requestBaseUrl(HttpServletRequest req) {
        String scheme = req.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.trim().isEmpty()) {
            scheme = req.getScheme();
        }
        String host = req.getHeader("X-Forwarded-Host");
        if (host == null || host.trim().isEmpty()) {
            host = req.getServerName() + ":" + req.getServerPort();
        }
        String ctx = req.getContextPath();
        if (ctx == null) {
            ctx = "";
        }
        return scheme + "://" + host + ctx;
    }

    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}
