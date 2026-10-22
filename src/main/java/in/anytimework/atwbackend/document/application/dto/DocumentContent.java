package in.anytimework.atwbackend.document.application.dto;

/** A document's bytes plus the metadata needed to serve them. */
public record DocumentContent(String filename, String contentType, byte[] data) {
}
