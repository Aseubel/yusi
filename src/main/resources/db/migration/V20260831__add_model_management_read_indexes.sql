ALTER TABLE `model_call_trace`
    ADD KEY `idx_model_call_trace_model_created` (`model_id`, `created_at`),
    ADD KEY `idx_model_call_trace_scene_model_created` (`scene`, `model_id`, `created_at`);
