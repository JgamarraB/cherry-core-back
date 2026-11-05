package com.cherrycore.identity.application.port;

import com.cherrycore.identity.domain.AuthEvent;

public interface AuthEventRecorder {

    void record(AuthEvent event);
}
