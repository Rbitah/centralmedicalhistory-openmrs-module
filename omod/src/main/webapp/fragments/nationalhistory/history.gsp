<div class="info-section">
    <div class="info-header">
        <i class="icon-book"></i>
        <h3>NATIONAL MEDICAL HISTORY</h3>
    </div>
    <div class="info-body">
        <div id="nationalhistory-loading">Loading national medical history...</div>
        <div id="nationalhistory-empty" style="display:none;">No national medical history available.</div>
        <div id="nationalhistory-error" style="display:none;">Unable to load national medical history.</div>
        <ul id="nationalhistory-list" style="display:none; list-style:none; margin:0; padding:0;"></ul>
    </div>
</div>

<script type="text/javascript">
    (function () {
        var patientUuid = '${config.patient?.patient?.uuid ?: ""}';
        var endpoint = '${ui.contextPath()}/ws/rest/v1/nationalhistory/' + patientUuid;

        function escapeHtml(value) {
            return jq('<div/>').text(value || '').html();
        }

        function renderRecords(records) {
            var list = jq('#nationalhistory-list');
            list.empty();

            jq.each(records, function (index, record) {
                var facility = record.facility || 'N/A';
                var type = record.type || 'N/A';
                var summary = record.summary || 'N/A';
                var date = record.date || 'N/A';

                var html = '' +
                    '<li style="margin-bottom:12px; padding-bottom:10px; border-bottom:1px solid #dddddd;">' +
                        '<div><strong>' + escapeHtml(facility) + '</strong></div>' +
                        '<div>Type: ' + escapeHtml(type) + '</div>' +
                        '<div>Summary: ' + escapeHtml(summary) + '</div>' +
                        '<div>Date: ' + escapeHtml(date) + '</div>' +
                    '</li>';

                list.append(html);
            });
        }

        if (!patientUuid) {
            jq('#nationalhistory-loading').hide();
            jq('#nationalhistory-error').text('Unable to determine patient identifier for national medical history.').show();
            return;
        }

        jq.ajax({
            url: endpoint,
            method: 'GET',
            dataType: 'json'
        }).done(function (response) {
            jq('#nationalhistory-loading').hide();

            if (!response || !response.records || response.records.length === 0) {
                jq('#nationalhistory-empty').show();
                return;
            }

            renderRecords(response.records);
            jq('#nationalhistory-list').show();
        }).fail(function () {
            jq('#nationalhistory-loading').hide();
            jq('#nationalhistory-error').show();
        });
    })();
</script>
