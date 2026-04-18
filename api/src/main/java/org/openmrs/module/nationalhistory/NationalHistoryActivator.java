package org.openmrs.module.nationalhistory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.BaseModuleActivator;

public class NationalHistoryActivator extends BaseModuleActivator {

    private final Log log = LogFactory.getLog(this.getClass());

    @Override
    public void started() {
        log.info("Started National History module");
    }

    @Override
    public void stopped() {
        log.info("Stopped National History module");
    }
}
