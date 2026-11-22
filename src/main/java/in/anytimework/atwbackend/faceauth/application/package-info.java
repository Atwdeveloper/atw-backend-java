/**
 * Public API of the faceauth module (Spring Modulith named interface "api"): other modules call
 * {@code FaceAuthService} to enroll/check/remove a worker's face template. {@code FaceAuthServiceImpl}
 * and {@code FaceEnrollmentPhotoSupport} are package-private and remain inaccessible across module
 * boundaries, the same shape as {@code kyc.application}/{@code document.application}.
 */
@org.springframework.modulith.NamedInterface("api")
package in.anytimework.atwbackend.faceauth.application;
