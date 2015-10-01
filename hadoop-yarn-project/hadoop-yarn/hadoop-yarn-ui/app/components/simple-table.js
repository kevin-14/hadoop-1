import Ember from 'ember';
import ChartUtilsMixin from 'yarn-ui/mixins/charts-utils';

export default Ember.Component.extend(ChartUtilsMixin, {
  didInsertElement: function() {
    $('#' + this.get('table-id')).DataTable();
  }
});