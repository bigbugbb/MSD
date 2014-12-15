from django.conf.urls import url

from song import views

urlpatterns = [

	url(r'^profile/$', views.profile_index, name='profile_index'),

    url(r'^updates/$', views.update_data, name='update_data'),

    url(r'^predict/(?P<release_id>[0-9]+)/$', views.predict_year, name='predict_year'),

    url(r'^recommend/(?P<release_id>[0-9]+)/$', views.recommend_songs, name='recommend_songs')
]