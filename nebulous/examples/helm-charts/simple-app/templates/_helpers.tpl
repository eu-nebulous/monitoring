{{/* _helpers.tpl */}}
{{- define "simple-app.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name }}
{{- end -}}

{{- define "simple-app.labels" -}}
app.kubernetes.io/name: {{ include "simple-app.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "simple-app.selectorLabels" -}}
app.kubernetes.io/name: {{ include "simple-app.name" . }}
{{- end -}}

{{- define "simple-app.name" -}}
{{- default "simple-app" .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}
