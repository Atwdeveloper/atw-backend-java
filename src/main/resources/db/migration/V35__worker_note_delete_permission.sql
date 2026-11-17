-- V35__worker_note_delete_permission.sql
-- New permission for hard-deleting a worker note (see AdminWorkerController.deleteNote /
-- WorkerNoteService.delete). Notes already split list (workers.view_comments) from create
-- (workers.edit) - delete gets its own third, dedicated code rather than reusing either, for the
-- same reason that split exists: a caller may be trusted to read or add remarks without being
-- trusted to remove one someone else wrote. Deliberately NOT auto-granted to any role, matching how
-- workers.view_bank_details/faceauth.identify were added in V31 - a developer must assign it
-- explicitly via the Roles screen.
INSERT INTO admin_permission (id, code, module, action, label) VALUES
    (gen_random_uuid(), 'workers.delete_comments', 'workers', 'delete_comments', 'Delete a worker note');
