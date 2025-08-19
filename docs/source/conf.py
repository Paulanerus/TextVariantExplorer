import os

owner = "Paulanerus"

project = 'TextVariant Explorer'
copyright = f'2025, {owner}'
author = owner

def _read_gradle_property(prop_name: str, file_path: str) -> str:
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            for raw in f:
                line = raw.strip()
                if not line or line.startswith('#') or '=' not in line:
                    continue
                key, value = line.split('=', 1)
                if key.strip() == prop_name:
                    return value.strip()
    except FileNotFoundError:
        pass
    return "0.0.0"

_ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
_GRADLE_PROPS = os.path.join(_ROOT_DIR, 'gradle.properties')
release = _read_gradle_property('app.version', _GRADLE_PROPS)

print("Version: ", release)

# -- General configuration ---------------------------------------------------

extensions = ['myst_parser']

templates_path = ['_templates']
exclude_patterns = []

html_baseurl = os.environ.get("READTHEDOCS_CANONICAL_URL", "/")

# -- Options for HTML output -------------------------------------------------

html_theme = 'sphinx_rtd_theme'
html_static_path = ['_static']
