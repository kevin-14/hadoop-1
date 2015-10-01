import Ember from 'ember';
import config from './config/environment';

var Router = Ember.Router.extend({
  location: config.locationType
});

Router.map(function() {
  this.route('yarnApps');
  this.route('yarnQueues');
  this.route('clusterOverview');
  this.route('yarnApp', { path: '/yarnApp/:app_id' })
});

export default Router;
