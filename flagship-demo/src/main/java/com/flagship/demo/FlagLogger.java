package com.flagship.demo;

import com.flagship.starter.FlagChange;
import com.flagship.starter.FlagChangeEvent;
import com.flagship.starter.FlagChangeListener;
import org.springframework.stereotype.Component;

/**
 * An application-side listener. It is just a bean: the starter finds it and
 * registers it, and it is called whenever flags change.
 */
@Component
public class FlagLogger implements FlagChangeListener {

    @Override
    public void onFlagsChanged(FlagChangeEvent event) {
        for (FlagChange change : event.changes()) {
            System.out.println(">>> FLAG CHANGE: " + change.flagKey() + " " + change.type());
        }
    }
}