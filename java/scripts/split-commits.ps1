# Split post-c6d383b work into 7 TAPD-tagged commits. Run from repo root: powershell -File scripts/split-commits.ps1
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

$U = "--user=YueChongqi"

function Commit-Dual([string]$title, [string]$s1, [string]$u1, [string]$s2, [string]$u2) {
    git commit -m $title -m "--story=$s1" -m $U -m "TAPD: $u1" -m "--story=$s2" -m $U -m "TAPD: $u2"
}

# 1 refresh token
git add -- `
  src/main/java/com/netdisk/controller/AuthController.java `
  src/main/java/com/netdisk/service/AuthService.java `
  src/main/java/com/netdisk/service/impl/AuthServiceImpl.java `
  src/main/java/com/netdisk/mapper/UserMapper.java `
  src/main/java/com/netdisk/mapper/UserSessionMapper.java `
  src/main/resources/mapper/UserMapper.xml `
  src/main/resources/mapper/UserSessionMapper.xml `
  src/main/java/com/netdisk/pojo/dto/LoginRequestDTO.java `
  src/main/java/com/netdisk/pojo/dto/RefreshRequestDTO.java `
  src/main/java/com/netdisk/pojo/dto/RegisterRequestDTO.java `
  src/main/java/com/netdisk/pojo/dto/SendVerificationRequestDTO.java `
  src/main/java/com/netdisk/pojo/entity/User.java `
  src/main/java/com/netdisk/pojo/entity/UserSession.java
git add -u -- `
  src/main/java/com/netdisk/pojo/dto/LoginRequest.java `
  src/main/java/com/netdisk/pojo/dto/RefreshRequest.java `
  src/main/java/com/netdisk/pojo/dto/RegisterRequest.java `
  src/main/java/com/netdisk/pojo/dto/SendVerificationRequest.java `
  src/main/java/com/netdisk/mapper/UserRepository.java `
  src/main/java/com/netdisk/mapper/UserSessionRepository.java `
  src/main/resources/mapper/UserRepository.xml `
  src/main/resources/mapper/UserSessionRepository.xml `
  src/main/java/com/netdisk/pojo/entity/UserEntity.java `
  src/main/java/com/netdisk/pojo/entity/UserSessionEntity.java
git commit -m "feat: refresh token" -m "--story=1030412@tapd-49487926" -m $U -m "TAPD: https://www.tapd.cn/49487926/s/1277419"

# 2 kick session (empty; code in AuthController/AuthServiceImpl)
git commit --allow-empty -m "chore: kick device session (same files as refresh token commit)" -m "--story=1030411@tapd-49487926" -m $U -m "TAPD: https://www.tapd.cn/49487926/s/1277421"

# 3 multipart upload
git add -- `
  src/main/java/com/netdisk/controller/UploadController.java `
  src/main/java/com/netdisk/service/UploadService.java `
  src/main/java/com/netdisk/service/impl/UploadServiceImpl.java `
  src/main/java/com/netdisk/mapper/UploadPartMapper.java `
  src/main/java/com/netdisk/mapper/UploadSessionMapper.java `
  src/main/java/com/netdisk/mapper/StorageObjectMapper.java `
  src/main/resources/mapper/UploadPartMapper.xml `
  src/main/resources/mapper/UploadSessionMapper.xml `
  src/main/resources/mapper/StorageObjectMapper.xml `
  src/main/java/com/netdisk/pojo/dto/UploadCompleteRequestDTO.java `
  src/main/java/com/netdisk/pojo/dto/UploadInitRequestDTO.java `
  src/main/java/com/netdisk/pojo/entity/UploadPart.java `
  src/main/java/com/netdisk/pojo/entity/UploadSession.java `
  src/main/java/com/netdisk/pojo/entity/StorageObject.java
git add -u -- `
  src/main/java/com/netdisk/mapper/UploadPartRepository.java `
  src/main/java/com/netdisk/mapper/UploadSessionRepository.java `
  src/main/java/com/netdisk/mapper/StorageObjectRepository.java `
  src/main/resources/mapper/UploadPartRepository.xml `
  src/main/resources/mapper/UploadSessionRepository.xml `
  src/main/resources/mapper/StorageObjectRepository.xml `
  src/main/java/com/netdisk/pojo/dto/UploadCompleteRequest.java `
  src/main/java/com/netdisk/pojo/dto/UploadInitRequest.java `
  src/main/java/com/netdisk/pojo/entity/UploadPartEntity.java `
  src/main/java/com/netdisk/pojo/entity/UploadSessionEntity.java `
  src/main/java/com/netdisk/pojo/entity/StorageObjectEntity.java
git commit -m "feat: chunked upload and resume" -m "--story=1030409@tapd-49487926" -m $U -m "TAPD: https://www.tapd.cn/49487926/s/1277423"

# 4 upload integrity (empty; SHA in UploadServiceImpl)
git commit --allow-empty -m "chore: upload integrity check (SHA in UploadServiceImpl)" -m "--story=1030408@tapd-49487926" -m $U -m "TAPD: https://www.tapd.cn/49487926/s/1277425"

# 5 directory ops + recycle bin (dual TAPD)
git add -- `
  src/main/java/com/netdisk/controller/ResourceController.java `
  src/main/java/com/netdisk/controller/LocalFileAccessController.java `
  src/main/java/com/netdisk/service/ResourceService.java `
  src/main/java/com/netdisk/service/impl/ResourceServiceImpl.java `
  src/main/java/com/netdisk/service/UserResourceInitService.java `
  src/main/java/com/netdisk/service/impl/UserResourceInitServiceImpl.java `
  src/main/java/com/netdisk/mapper/ResourceMapper.java `
  src/main/resources/mapper/ResourceMapper.xml `
  src/main/java/com/netdisk/mapper/SpaceMapper.java `
  src/main/resources/mapper/SpaceMapper.xml `
  src/main/java/com/netdisk/pojo/dto/CopyResourcesRequestDTO.java `
  src/main/java/com/netdisk/pojo/dto/CreateFolderRequestDTO.java `
  src/main/java/com/netdisk/pojo/dto/MoveResourcesRequestDTO.java `
  src/main/java/com/netdisk/pojo/dto/RenameResourceRequestDTO.java `
  src/main/java/com/netdisk/pojo/dto/PurgeRecycleRequestDTO.java `
  src/main/java/com/netdisk/pojo/entity/RecycleBinRow.java `
  src/main/java/com/netdisk/pojo/vo/RecycleBinItemVO.java `
  src/main/java/com/netdisk/pojo/vo/RecycleBinListResponseVO.java `
  src/main/java/com/netdisk/security/FileAccessSigner.java `
  src/main/java/com/netdisk/pojo/entity/Resource.java `
  src/main/java/com/netdisk/pojo/entity/RecycleEntry.java `
  src/main/java/com/netdisk/pojo/entity/Space.java `
  src/main/java/com/netdisk/pojo/entity/SpaceMember.java `
  src/main/java/com/netdisk/pojo/entity/ResourceAcl.java `
  src/main/java/com/netdisk/pojo/entity/AbacPolicy.java `
  src/main/java/com/netdisk/pojo/entity/Share.java `
  src/main/java/com/netdisk/pojo/entity/ShareAccessLog.java `
  src/main/java/com/netdisk/pojo/entity/ShareResource.java `
  src/main/java/com/netdisk/config/AppProperties.java `
  src/main/java/com/netdisk/config/SecurityConfig.java `
  src/main/resources/application.yml
git add -u -- `
  src/main/java/com/netdisk/mapper/ResourceRepository.java `
  src/main/java/com/netdisk/mapper/SpaceRepository.java `
  src/main/resources/mapper/ResourceRepository.xml `
  src/main/resources/mapper/SpaceRepository.xml `
  src/main/java/com/netdisk/pojo/entity/ResourceEntity.java `
  src/main/java/com/netdisk/pojo/entity/ResourceAclEntity.java `
  src/main/java/com/netdisk/pojo/entity/RecycleEntryEntity.java `
  src/main/java/com/netdisk/pojo/entity/SpaceEntity.java `
  src/main/java/com/netdisk/pojo/entity/SpaceMemberEntity.java `
  src/main/java/com/netdisk/pojo/entity/AbacPolicyEntity.java `
  src/main/java/com/netdisk/pojo/entity/ShareEntity.java `
  src/main/java/com/netdisk/pojo/entity/ShareAccessLogEntity.java `
  src/main/java/com/netdisk/pojo/entity/ShareResourceEntity.java
Commit-Dual "feat: directory CRUD/move/copy and recycle bin" `
  "1030407@tapd-49487926" "https://www.tapd.cn/49487926/s/1277427" `
  "1030406@tapd-49487926" "https://www.tapd.cn/49487926/s/1277429"

# 6 TAPD 1030406 pointer (empty)
git commit --allow-empty -m "chore: TAPD 1030406 recycle bin pointer (code in previous commit)" -m "--story=1030406@tapd-49487926" -m $U -m "TAPD: https://www.tapd.cn/49487926/s/1277429"

# 7 audit / activity
git add -- `
  .gitignore `
  pom.xml `
  src/main/java/com/netdisk/audit/ResourceOperationAuditAspect.java `
  src/main/java/com/netdisk/service/AuditLogRecorder.java `
  src/main/java/com/netdisk/mapper/AuditLogMapper.java `
  src/main/resources/mapper/AuditLogMapper.xml `
  src/main/java/com/netdisk/controller/ActivityController.java `
  src/main/java/com/netdisk/service/ActivityService.java `
  src/main/java/com/netdisk/service/impl/ActivityServiceImpl.java `
  src/main/java/com/netdisk/pojo/vo/ActivityItemVO.java `
  src/main/java/com/netdisk/pojo/vo/ActivityListResponseVO.java `
  src/main/java/com/netdisk/pojo/entity/AuditLog.java
git add -u -- src/main/java/com/netdisk/pojo/entity/AuditLogEntity.java
git commit -m "feat: audit log and resource operation tracing" -m "--story=1030399@tapd-49487926" -m $U -m "TAPD: https://www.tapd.cn/49487926/s/1277433"

git status --short
