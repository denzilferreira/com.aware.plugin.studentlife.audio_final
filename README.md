AWARE Plugin: Conversations
=========================================

[![Release](https://jitpack.io/v/denzilferreira/com.aware.plugin.studentlife.audio_final.svg)](https://jitpack.io/#denzilferreira/com.aware.plugin.studentlife.audio_final)

This plugin detects if the user is engaged in a conversation or not. It does not store the raw audio (it's disabled). 
This plugin was developed as a collaboration between Cornell and Dartmouth College for the StudentLife project and later extended to support the converstations start/end at the Center for Ubiquitous Computing at the University of Oulu.

# Settings
Parameters adjustable on the dashboard and client: 
- **status_plugin_studentlife_audio**: (boolean) activate/deactivate plugin

# Broadcasts
**ACTION_AWARE_PLUGIN_CONVERSATIONS_START**
Broadcasted when we detect the start of a conversation

**ACTION_AWARE_PLUGIN_CONVERSATIONS_STOP**
Broadcasted when we detect the end of a conversation

# Providers
## Conversations Data
> content://com.aware.plugin.studentlife.audio_final.provider.audio_final/plugin_studentlife_audio_android

Field | Type | Description
----- | ---- | -----------
_id | INTEGER | primary key auto-incremented
timestamp | REAL | unix timestamp in milliseconds of sample
device_id | TEXT | AWARE device ID
datatype | INTEGER |    ?
double_energy | REAL |  ?
inference | INTEGER |   ?
blob_feature | BLOB |   ?
double_convo_start | REAL | ?
double_convo_end | REAL |   ?