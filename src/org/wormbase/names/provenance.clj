(ns org.wormbase.names.provenance)


  ;; For each key in the provenance schema:
  ;;  if the key does not exist in a name record, then infer from the request
  ;;  finally conform each name record to the spec.

  ;; TODO: don't bother allowing them to be specied in header
  ;;        use provenance from payload(s) or infer programmatically
  ;;        from the request.

(defn obtain
  "Obtain a provenenace map from name-records and/or the request.
  Attributes found in `name-records` take precedence over those in the
  request."
  [request record]
  (into {} (filter #(= (namespace (key %)) "provenance") record)))
