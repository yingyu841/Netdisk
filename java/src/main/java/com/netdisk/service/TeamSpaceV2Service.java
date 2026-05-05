package com.netdisk.service;

import java.io.InputStream;
import java.util.Map;

public interface TeamSpaceV2Service {
    Map<String, Object> createGroup(String userUuid, Map<String, Object> request);

    Map<String, Object> listMyGroups(String userUuid, String keyword, Integer page, Integer pageSize);

    Map<String, Object> getGroupDetail(String userUuid, String groupId);

    Map<String, Object> updateGroup(String userUuid, String groupId, Map<String, Object> request);

    Map<String, Object> archiveGroup(String userUuid, String groupId);

    Map<String, Object> deleteGroup(String userUuid, String groupId);

    Map<String, Object> listMembers(String userUuid, String groupId, String keyword, Integer page, Integer pageSize);

    Map<String, Object> inviteMember(String userUuid, String groupId, Map<String, Object> request);

    Map<String, Object> listInvites(String userUuid, String groupId, String status, Integer page, Integer pageSize);

    Map<String, Object> cancelInvite(String userUuid, String groupId, String inviteId);

    Map<String, Object> acceptInvite(String userUuid, String inviteToken);

    Map<String, Object> rejectInvite(String userUuid, String inviteToken);

    Map<String, Object> updateMemberRole(String userUuid, String groupId, String targetUserId, Map<String, Object> request);

    Map<String, Object> removeMember(String userUuid, String groupId, String targetUserId);

    Map<String, Object> createJoinRequest(String userUuid, String groupId, Map<String, Object> request);

    Map<String, Object> listMyJoinRequests(String userUuid, Integer page, Integer pageSize);

    Map<String, Object> listGroupJoinRequests(String userUuid, String groupId, String status, Integer page, Integer pageSize);

    Map<String, Object> approveJoinRequest(String userUuid, String groupId, String requestId);

    Map<String, Object> rejectJoinRequest(String userUuid, String groupId, String requestId);

    Map<String, Object> favoriteResource(String userUuid, String resourceId);

    Map<String, Object> unfavoriteResource(String userUuid, String resourceId);

    Map<String, Object> listMyFavorites(String userUuid, Integer page, Integer pageSize);

    Map<String, Object> createTag(String userUuid, Map<String, Object> request);

    Map<String, Object> listTags(String userUuid);

    Map<String, Object> updateTag(String userUuid, String tagId, Map<String, Object> request);

    Map<String, Object> deleteTag(String userUuid, String tagId);

    Map<String, Object> addTagToResource(String userUuid, String resourceId, String tagId);

    Map<String, Object> removeTagFromResource(String userUuid, String resourceId, String tagId);

    Map<String, Object> listResourceTags(String userUuid, String resourceId);

    Map<String, Object> listResourceVersions(String userUuid, String resourceId, Integer page, Integer pageSize);

    Map<String, Object> initResourceVersionUpload(String userUuid, String resourceId, Map<String, Object> request);

    Map<String, Object> uploadResourceVersionPart(String userUuid, String resourceId, String uploadId, Integer partNumber, InputStream stream);

    Map<String, Object> completeResourceVersionUpload(String userUuid, String resourceId, String uploadId, Map<String, Object> request);

    Map<String, Object> restoreResourceVersion(String userUuid, String resourceId, Integer versionNo);

    Map<String, Object> getResourceVersionDownloadUrl(String userUuid, String resourceId, Integer versionNo, String requestBaseUrl);

    Map<String, Object> createPreviewJob(String userUuid, String resourceId, Map<String, Object> request);

    Map<String, Object> getPreviewJob(String userUuid, String jobId);

    Map<String, Object> listPreviewJobs(String userUuid, String status, Integer page, Integer pageSize);

    Map<String, Object> updateSharePolicy(String userUuid, String shareId, Map<String, Object> request);

    Map<String, Object> listShareAccessLogs(String userUuid, String shareId, Integer page, Integer pageSize);

    Map<String, Object> getShareStats(String userUuid, String shareId);

    Map<String, Object> listNotifications(String userUuid, Boolean unreadOnly, Integer page, Integer pageSize);

    Map<String, Object> readNotification(String userUuid, String notificationId);

    Map<String, Object> readAllNotifications(String userUuid);

    Map<String, Object> deleteNotification(String userUuid, String notificationId);

    Map<String, Object> getPublicGroup(String groupId);

    Map<String, Object> listPublicGroupResources(String groupId, Integer page, Integer pageSize);
}
