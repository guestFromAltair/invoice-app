import React from 'react';
import ReactDOM from 'react-dom/client';
import {Provider} from 'react-redux';
import {BrowserRouter} from 'react-router-dom';
import {Toaster} from 'sonner';
import {store} from './store';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
        <Provider store={store}>
            <BrowserRouter>
                <App/>
                <Toaster richColors position="top-right" duration={4000}/>
            </BrowserRouter>
        </Provider>
    </React.StrictMode>
);