ALTER TABLE `model_call_trace`
    ADD KEY `idx_model_call_trace_user_run_created` (`user_id`, `run_id`, `created_at`);
