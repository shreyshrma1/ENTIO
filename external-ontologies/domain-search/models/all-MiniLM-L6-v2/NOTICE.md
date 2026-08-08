# Local embedding model notice

Entio distributes `sentence-transformers/all-MiniLM-L6-v2` solely for local
Phase 13 search. The pinned Hugging Face revision is
`94ea1512acaefbfe2e255b2d2ea4bf0d9d7b3dc3`; the ONNX artifact is
`onnx/model.onnx`. The model and tokenizer are licensed under Apache License
2.0. See `LICENSE-APACHE-2.0.txt` in this directory.

Local inference also uses DJL and DJL Hugging Face tokenizers (Apache-2.0),
Microsoft ONNX Runtime (MIT), and Apache Lucene (Apache-2.0). Their dependency
metadata and license notices remain available through the pinned Maven
artifacts. No hosted model or runtime download is used by Entio.

Model source: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
